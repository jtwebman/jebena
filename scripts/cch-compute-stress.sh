#!/usr/bin/env bash
# ConcurrentHashMap.computeIfAbsent atomicity under concurrency: 8 fibers x 500 do
# computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet() over 5 shared
# keys. computeIfAbsent must create exactly ONE AtomicInteger per key even when
# fibers race on the same absent key (synchronized on the map); the mapping lambda
# runs under that lock. Total = 8*500 = 4000 over 5 keys -> demo() = 40005. Must
# match real java at carriers 1 & 4 and with GC forced (moving GC remaps the map).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-cchc
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/CchComputeStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.CchComputeStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 40 env $1 "$JEBENA" run st/CchComputeStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "cch-compute-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "cch-compute-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 3
check "JEBENA_CARRIERS=4" 8
check "JEBENA_GC_INTERVAL=250 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "cch-compute-stress: OK — ConcurrentHashMap.computeIfAbsent single-create atomic across carriers (1 & 4, +GC) = $EXP, matches real java"
