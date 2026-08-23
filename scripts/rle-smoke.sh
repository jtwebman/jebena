#!/usr/bin/env bash
# Run-length encode/decode round-trip: encode("aaabbc")->"a3b2c1", decode back, assert
# equal for several inputs incl. multi-digit runs + empty string. Exercises StringBuilder,
# Character.isDigit (eager jbase path), char arithmetic. stdout byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-rle
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/RunLength.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" RunLength)"
got="$(timeout 60 "$JEBENA" run RunLength main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "rle-smoke: OK (run-length round-trip byte-identical to real java)"; exit 0
else echo "rle-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head; exit 1; fi
