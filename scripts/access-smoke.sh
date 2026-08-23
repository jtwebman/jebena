#!/usr/bin/env bash
# Access-log summarizer end-to-end: String.lines(), LinkedHashMap access-order + removeEldestEntry
# LRU, Collections.sort with a chained Comparator, TreeMap ordered tally, and Formatter grouping
# (%,d / %,.2f). stdout must be byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-access
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/AccessLog.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(find "$OUT" -name '*.class')
want="$("$JAVA" -cp "$OUT" AccessLog)"
got="$(timeout 60 "$JEBENA" run AccessLog main "${APP[@]}" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "access-smoke: OK (lines()+LinkedHashMap-LRU+Comparator+TreeMap+Formatter-grouping byte-identical to real java)"; exit 0
else echo "access-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head -40; exit 1; fi
