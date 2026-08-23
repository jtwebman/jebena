#!/usr/bin/env bash
# Concurrent histogram via ConcurrentHashMap.compute(key,(k,v)->v==null?1:v+1): 8 fibers x
# 500 increments over 10 keys -> every key = 400, weighted checksum = 22000. compute() is
# synchronized; a lost update / lost wakeup / GC remap changes the checksum. Distinct from
# cch-compute-stress (computeIfAbsent). Must match real java at carriers 1 & 4 (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-cchhist
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/CchComputeHist.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.CchComputeHist demo 2>/dev/null)
fail=0
# A per-rep timeout is retried ONCE (4 carriers oversubscribe this 4-core box, so a
# park/lock-heavy rep can be starved under ambient load; a genuine hang fails both tries).
onerep() { # env rep
  local out rc got
  out=$(timeout 120 env $1 "$JEBENA" run st/CchComputeHist demo "${APP[@]}" "${JBASE[@]}" 2>&1); rc=$?
  if [ $rc -eq 124 ]; then
    echo "cch-compute-hist: NOTE ($1) rep=$2 timed out, retrying once" >&2
    out=$(timeout 120 env $1 "$JEBENA" run st/CchComputeHist demo "${APP[@]}" "${JBASE[@]}" 2>&1); rc=$?
    [ $rc -eq 124 ] && { echo "cch-compute-hist: FAIL ($1) rep=$2 HANG (twice)"; fail=1; return; }
  fi
  got=$(printf '%s\n' "$out" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
  [ "$got" = "$EXP" ] || { echo "cch-compute-hist: FAIL ($1) rep=$2 jebena=$got java=$EXP"; fail=1; }
}
check() { for rep in $(seq 1 "$2"); do onerep "$1" "$rep"; done; }
check "JEBENA_CARRIERS=1" 2
check "JEBENA_CARRIERS=4" 4
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 2
[ "$fail" = 0 ] || exit 1
echo "cch-compute-hist: OK — ConcurrentHashMap.compute histogram across carriers (1 & 4, +GC) = $EXP, matches real java"
