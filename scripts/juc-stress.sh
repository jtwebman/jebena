#!/usr/bin/env bash
# java.util.concurrent synchronizers built on the intrinsic monitors + wait/notify:
# CountDownLatch (LatchStress: main await()s 8 workers' countDown() -> 8000) and
# Semaphore (SemStress: Semaphore(1) mutex around a non-atomic counter -> 4000).
# Both must match real java at 1 & 4 carriers and with GC forced.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-juc
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/LatchStress.java "$ROOT"/test/stress/SemStress.java \
  "$ROOT"/test/stress/RLockStress.java "$ROOT"/test/stress/CondStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
check() { # $1 main  $2 label  $3 env  $4 reps  $5 exp
  for rep in $(seq 1 "$4"); do
    ALL=$(timeout 40 bash -c "$3 '$JEBENA' run st/$1 demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "juc-stress: FAIL $1 $2 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$5" ] || { echo "juc-stress: FAIL $1 $2 rep=$rep jebena=$GOT java=$5"; fail=1; }
  done
}
# CountDownLatch, Semaphore, ReentrantLock (mutex+reentrancy), ReentrantLock+Condition.
for main in LatchStress SemStress RLockStress CondStress; do
  EXP=$("$JAVA" -cp "$OUT" Driver "st.$main" demo 2>/dev/null)
  check "$main" "carriers=1" "JEBENA_CARRIERS=1" 3 "$EXP"
  check "$main" "carriers=4" "JEBENA_CARRIERS=4" 8 "$EXP"
  check "$main" "carriers=4+GC" "JEBENA_GC_INTERVAL=300 JEBENA_CARRIERS=4" 4 "$EXP"
done
[ "$fail" = 0 ] || exit 1
echo "juc-stress: OK — CountDownLatch + Semaphore + ReentrantLock + Condition on monitors/wait-notify (carriers 1 & 4, +GC), matches real java"
