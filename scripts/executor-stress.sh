#!/usr/bin/env bash
# java.util.concurrent thread-pool stress: a fixed pool of 4 worker fibers runs 100
# Callables (each returns its index); the main fiber collects all 100 Futures and
# sums their get() -> 0..99 = 4950. Exercises Executors.newFixedThreadPool +
# ThreadPoolExecutor + FutureTask + Future.get: workers park on the queue's take(),
# get() parks until each task completes, so the pool runs at carriers=1 as well as
# 4 (+GC). Deterministic (4950), matches real java.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-exec
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/ExecutorStress.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"
EXP=$("$JAVA" -cp "$OUT" Driver st.ExecutorStress demo 2>/dev/null)
fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 30 bash -c "$2 exec '$JEBENA' run st/ExecutorStress demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "executor-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "executor-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 4
check "carriers=1+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=1" 3
check "carriers=4" "JEBENA_CARRIERS=4" 10
check "carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "executor-stress: OK — Executors.newFixedThreadPool(4) runs 100 tasks, Future.get sums = $EXP (carriers 1 & 4, +GC), matches real java"
