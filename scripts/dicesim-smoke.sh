#!/usr/bin/env bash
# Deterministic Monte-Carlo simulator end-to-end: java.util.SplittableRandom(fixedSeed)
# (bit-exact SplitMix64) drives 1000 dice rolls + values + time deltas; java.time.Instant +
# Clock.fixed advance a virtual clock; java.util.IntSummaryStatistics aggregates. Because the
# RNG matches the JDK bit-for-bit, the whole report (histogram, stats, final instant) is
# byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-dicesim
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/DiceSim.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" DiceSim)"
got="$(timeout 60 "$JEBENA" run DiceSim main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "dicesim-smoke: OK (SplittableRandom Monte-Carlo + Instant/Clock report byte-identical to real java)"; exit 0
else echo "dicesim-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head -20; exit 1; fi
