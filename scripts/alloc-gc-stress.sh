#!/usr/bin/env bash
# Allocation + moving-GC stress. 8 fibers each allocate a fresh array every
# iteration while computing a deterministic checksum; with a tiny
# JEBENA_GC_INTERVAL the moving mark-compact GC fires hundreds of times mid-run,
# so a mis-rooted or mis-remapped live object would corrupt the total or crash.
#
# Runs at the DEFAULT single carrier only. Concurrent allocation at
# JEBENA_CARRIERS>1 is NOT yet safe -- the heap object table is read locklessly
# but grown under a lock, so a get() racing a put()'s realloc segfaults (see
# docs/THREADING.md "Known limitations"). The no-alloc thread-stress.sh already
# covers real parallel carriers; this script covers the moving GC under heavy
# allocation, which is the single-carrier correctness that must never regress.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-alloc-gc
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/AllocStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.AllocStress demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
# 1 carrier: moving GC forced at several intervals.
for interval in 200 500 2000; do
  for rep in 1 2 3; do
    ALL=$(timeout 40 bash -c "JEBENA_GC_INTERVAL=$interval JEBENA_CARRIERS=1 '$JEBENA' run st/AllocStress demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "alloc-gc-stress: FAIL c=1 interval=$interval rep=$rep HANG (timeout)"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "alloc-gc-stress: FAIL c=1 interval=$interval rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
done
# 4 REAL carriers: concurrent allocation, and concurrent moving GC. This is the
# hard case -- lockless get() on the paged object table while other carriers
# allocate + a stop-the-world collector compacts. 8 reps each, must be exact AND
# genuinely parallel (>=2 carriers ran).
for interval in 0 1000 300; do
  env="JEBENA_CARRIERS=4"; [ "$interval" != 0 ] && env="JEBENA_GC_INTERVAL=$interval $env"
  for rep in $(seq 1 8); do
    ALL=$(timeout 40 bash -c "JEBENA_CARRIER_TRACE=1 $env '$JEBENA' run st/AllocStress demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "alloc-gc-stress: FAIL c=4 interval=$interval rep=$rep HANG (timeout)"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    RAN=$(printf '%s\n' "$ALL" | sed -n 's/.*carriers-ran=\([0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "alloc-gc-stress: FAIL c=4 interval=$interval rep=$rep jebena=$GOT java=$EXP"; fail=1; }
    [ "${RAN:-0}" -ge 2 ] || { echo "alloc-gc-stress: FAIL c=4 interval=$interval rep=$rep not parallel (ran=${RAN:-0})"; fail=1; }
  done
done
[ "$fail" = 0 ] || exit 1
echo "alloc-gc-stress: OK — 8 fibers x2000 allocs = $EXP; single-carrier moving GC + 4 REAL carriers concurrent alloc & concurrent moving GC, matches real java"
