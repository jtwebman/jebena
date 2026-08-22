#!/usr/bin/env bash
# Fiber-error isolation stress. 8 workers: worker 0 recurses infinitely (uncaught
# VM CallDepthExceeded in jebena / StackOverflowError in real java); workers 1..7
# add (k+1) to a shared AtomicLong; main joins all and returns the total (35).
# A single fiber's uncaught error must terminate ONLY that fiber -- not kill the
# carrier (which would deadlock a concurrent GC's safepoint) and not fail the
# program. Proves the safepoint/carrier-exit hardening: no hang, correct 35, at
# carriers 1 & 4 (+GC). Classpath passed via bash ARRAYS (eager, unmangled).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-inj
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/InjectErr.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)
EXP=$("$JAVA" -cp "$OUT" Driver st.InjectErr demo 2>/dev/null)
fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 30 env $2 "$JEBENA" run st/InjectErr demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "inject-err-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "inject-err-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 5
check "carriers=4" "JEBENA_CARRIERS=4" 12
check "carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 8
[ "$fail" = 0 ] || exit 1
echo "inject-err-stress: OK — one worker's uncaught error is isolated (no hang, no carrier death), total = $EXP (carriers 1 & 4, +GC), matches real java"
