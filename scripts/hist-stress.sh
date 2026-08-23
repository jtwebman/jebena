#!/usr/bin/env bash
# Concurrent histogram over a shared ConcurrentHashMap via merge(key,1,(a,b)->a+b):
# 8 fibers x 500 increments over 10 keys -> every key = 400, weighted checksum = 22000.
# merge() is synchronized; a lost update / lost wakeup / GC remap of the boxed values
# under contention changes the checksum. Must match real java at carriers 1 & 4 (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-hist
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/HistStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.HistStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 90 env $1 "$JEBENA" run st/HistStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "hist-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "hist-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 2
check "JEBENA_CARRIERS=4" 5
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 2
[ "$fail" = 0 ] || exit 1
echo "hist-stress: OK — ConcurrentHashMap.merge histogram across carriers (1 & 4, +GC) = $EXP, matches real java"
