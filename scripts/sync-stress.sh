#!/usr/bin/env bash
# Monitor (synchronized) mutual-exclusion stress. 8 fibers each do
# `synchronized (lock) { count++; }` 1000x on a shared NON-atomic static int.
# Without real per-object monitors the increments interleave across carriers and
# lose updates; with reentrant monitors count == 8000 exactly. Runs at 1 & 4
# carriers, and at 4 carriers with GC forced (monitors held across allocation /
# safepoints). Deterministic: 8000.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-sync
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/SyncCounter.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.SyncCounter demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 40 bash -c "$2 '$JEBENA' run st/SyncCounter demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "sync-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "sync-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 3
check "carriers=4" "JEBENA_CARRIERS=4" 12
check "carriers=4+GC" "JEBENA_GC_INTERVAL=300 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "sync-stress: OK — 8 fibers x1000 synchronized count++ = $EXP (carriers 1 & 4, +GC), real mutual exclusion, matches real java"
