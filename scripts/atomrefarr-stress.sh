#!/usr/bin/env bash
# AtomicIntegerArray contention: 8 fibers x 500 getAndAdd across a shared 10-element array +
# CAS-spin increments on index 0. Every op is synchronized, so the weighted array checksum
# must be deterministic across carriers; a lost update / GC remap of the backing int[] changes
# it. Must match real java at carriers 1 & 2 (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-atomrefarr
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/AtomRefArrStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.AtomRefArrStress demo 2>/dev/null)
fail=0
# per-rep timeout retried ONCE (load-flake tolerance; a genuine hang fails both tries).
onerep() { # env rep
  local out rc got
  out=$(timeout 120 env $1 "$JEBENA" run st/AtomRefArrStress demo "${APP[@]}" "${JBASE[@]}" 2>&1); rc=$?
  if [ $rc -eq 124 ]; then
    out=$(timeout 120 env $1 "$JEBENA" run st/AtomRefArrStress demo "${APP[@]}" "${JBASE[@]}" 2>&1); rc=$?
    [ $rc -eq 124 ] && { echo "atomrefarr-stress: FAIL ($1) rep=$2 HANG (twice)"; fail=1; return; }
  fi
  got=$(printf '%s\n' "$out" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
  [ "$got" = "$EXP" ] || { echo "atomrefarr-stress: FAIL ($1) rep=$2 jebena=$got java=$EXP"; fail=1; }
}
check() { for rep in $(seq 1 "$2"); do onerep "$1" "$rep"; done; }
check "JEBENA_CARRIERS=1" 2
check "JEBENA_CARRIERS=2" 4
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=2" 2
[ "$fail" = 0 ] || exit 1
echo "atomrefarr-stress: OK — AtomicReferenceArray CAS contention across carriers (1 & 2, +GC) = $EXP, matches real java"
