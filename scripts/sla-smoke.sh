#!/usr/bin/env bash
# SLA-config parser end-to-end: java.util.Scanner tokenizing, java.time.Duration.parse +
# Period.parse (ISO-8601), java.text.NumberFormat grouped/decimal, Integer.parseInt(radix)/
# toHexString/toUnsignedString, java.util.TreeMap.ceilingEntry tier lookup. stdout must be
# byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-sla
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/SlaParser.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" SlaParser)"
got="$(timeout 60 "$JEBENA" run SlaParser main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "sla-smoke: OK (Scanner+Duration/Period.parse+NumberFormat+TreeMap.ceilingEntry byte-identical to real java)"; exit 0
else echo "sla-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head -20; exit 1; fi
