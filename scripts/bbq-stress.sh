#!/usr/bin/env bash
# Bounded-buffer producer/consumer over a shared ArrayDeque guarded by a user monitor
# with wait/notifyAll (capacity 8): 4 producers put 250 distinct values each (blocking
# when full), 4 consumers take + sum into an AtomicLong (blocking when empty) until a
# poison pill. Exercises Object.wait/notifyAll on a user lock under backpressure both
# ways + moving GC; distinct values catch any lost/dup item or lost wakeup. Expected
# 1,625,500. Must match real java at carriers 1 & 4 and with GC forced.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-bbq
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/BoundedBufferStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.BoundedBufferStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 60 env $1 "$JEBENA" run st/BoundedBufferStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "bbq-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "bbq-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 2
check "JEBENA_CARRIERS=4" 6
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 4
[ "$fail" = 0 ] || exit 1
echo "bbq-stress: OK — bounded-buffer producer/consumer (user wait/notifyAll) across carriers (1 & 4, +GC) = $EXP, matches real java"
