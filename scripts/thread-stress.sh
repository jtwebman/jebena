#!/usr/bin/env bash
# Green-thread stress: many fibers incrementing shared atomics, joined by main;
# jebena must match real java exactly. Runs at the default 1 carrier (cooperative,
# deterministic) and at JEBENA_CARRIERS=4 (REAL parallel std.Thread carriers,
# 4d-4-iii b2). At 4 carriers we also assert genuine parallelism: >=2 distinct
# carriers must have run work (via JEBENA_CARRIER_TRACE). Every run is timeout-
# guarded so a deadlock fails fast, and the multi-carrier case is repeated 10x to
# catch nondeterminism / lost updates. The total must be exact regardless.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-thread-stress
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
# Compile only StressMain (not test/stress/*.java: other stress mains like
# LoadStress depend on classpath-dir helper classes not present here).
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/StressMain.java "$ROOT"/test/stress/CurThread.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.StressMain demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
# 1 carrier: cooperative deterministic path, 3 reps.
for rep in 1 2 3; do
  ALL=$(timeout 30 bash -c "JEBENA_CARRIERS=1 '$JEBENA' run st/StressMain demo $APP $JBASE" 2>&1)
  [ $? -eq 124 ] && { echo "thread-stress: FAIL carriers=1 rep=$rep HANG (timeout)"; fail=1; }
  GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
  [ "$GOT" = "$EXP" ] || { echo "thread-stress: FAIL carriers=1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
done
# 4 carriers: REAL parallel std.Thread carriers, 10 reps; total exact AND >=2 carriers ran.
for rep in $(seq 1 10); do
  ALL=$(timeout 30 bash -c "JEBENA_CARRIER_TRACE=1 JEBENA_CARRIERS=4 '$JEBENA' run st/StressMain demo $APP $JBASE" 2>&1)
  rc=$?
  GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
  RAN=$(printf '%s\n' "$ALL" | sed -n 's/.*carriers-ran=\([0-9]*\).*/\1/p')
  [ $rc -eq 124 ] && { echo "thread-stress: FAIL carriers=4 rep=$rep HANG (timeout)"; fail=1; }
  [ "$GOT" = "$EXP" ] || { echo "thread-stress: FAIL carriers=4 rep=$rep total jebena=$GOT java=$EXP"; fail=1; }
  [ "${RAN:-0}" -ge 2 ] || { echo "thread-stress: FAIL carriers=4 rep=$rep not parallel (carriers-ran=${RAN:-0}, want >=2)"; fail=1; }
done
# Per-fiber Thread.currentThread(): 8 workers each see a Thread distinct from main.
CTEXP=$("$JAVA" -cp "$OUT" Driver st.CurThread demo 2>/dev/null)
for cfg in "JEBENA_CARRIERS=1" "JEBENA_CARRIERS=4"; do
  for rep in 1 2 3; do
    ALL=$(timeout 30 bash -c "$cfg '$JEBENA' run st/CurThread demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "thread-stress: FAIL CurThread $cfg rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$CTEXP" ] || { echo "thread-stress: FAIL CurThread $cfg rep=$rep jebena=$GOT java=$CTEXP"; fail=1; }
  done
done
[ "$fail" = 0 ] || exit 1
echo "thread-stress: OK — 8 fibers x1000 (AtomicInteger+AtomicLong) = $EXP; per-fiber currentThread = $CTEXP; carriers=1 deterministic + carriers=4 REAL parallel (>=2 carriers ran), matches real java"
