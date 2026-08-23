#!/usr/bin/env bash
# Sales-log report end-to-end: java.util.Base64 (decode name tokens + roundtrip),
# java.time.format.DateTimeFormatter (reformat ISO dates), java.util.stream.LongStream/
# DoubleStream (total/max/min/average), java.util.zip.CRC32 (checksum of the report), and
# the new String<->UTF-8 byte bridge. stdout must be byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-sales
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/SalesReport.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" SalesReport)"
got="$(timeout 60 "$JEBENA" run SalesReport main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "sales-smoke: OK (Base64+DateTimeFormatter+Long/DoubleStream+CRC32 report byte-identical to real java)"; exit 0
else echo "sales-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head -20; exit 1; fi
