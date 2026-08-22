#!/usr/bin/env bash
# wait/notify + monitor handoff stress. 8 workers each, under synchronized(lock),
# bump a shared counter and notifyAll(); main wait()s under the same monitor until
# all have reported. Exercises wait() releasing + reacquiring the monitor, notifyAll
# waking the waiter, and mutual exclusion -- at 1 & 4 carriers and with GC forced.
# Deterministic result: 8.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-wn
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/WaitNotify.java "$ROOT"/test/stress/ManyWait.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.WaitNotify demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 25 bash -c "$2 '$JEBENA' run st/WaitNotify demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "waitnotify-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "waitnotify-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 3
check "carriers=4" "JEBENA_CARRIERS=4" 15
check "carriers=4+GC" "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 8
MW=$("$JAVA" -cp "$OUT" Driver st.ManyWait demo 2>/dev/null)
mwcheck() { for rep in $(seq 1 "$2"); do ALL=$(timeout 30 bash -c "$1 '$JEBENA' run st/ManyWait demo $APP $JBASE" 2>&1); [ $? -eq 124 ] && { echo "waitnotify-stress: FAIL ManyWait HANG"; fail=1; }; G=$(printf '%s\n' "$ALL"|sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p'); [ "$G" = "$MW" ] || { echo "waitnotify-stress: FAIL ManyWait jebena=$G java=$MW"; fail=1; }; done; }
mwcheck "JEBENA_CARRIERS=1" 3
mwcheck "JEBENA_CARRIERS=4" 10
mwcheck "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 5
[ "$fail" = 0 ] || exit 1
echo "waitnotify-stress: OK — 8 workers notifyAll + main wait/reacquire = $EXP (carriers 1 & 4, +GC), matches real java"
