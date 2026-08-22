#!/usr/bin/env bash
# java.util.concurrent.ConcurrentHashMap under concurrent mutation: 8 fibers each
# do 500 merge() increments across 4 shared keys. merge must be an atomic
# read-modify-write (under the map's monitor) or updates are lost. Correct total =
# 8*500 = 4000, max single key = 1000 -> demo() = 40010. Must match real java at
# carriers 1 & 4 and with GC forced (the moving collector remaps the map's nodes).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-cch
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/CchStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.CchStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 40 env $1 "$JEBENA" run st/CchStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "cch-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "cch-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 3
check "JEBENA_CARRIERS=4" 8
check "JEBENA_GC_INTERVAL=250 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "cch-stress: OK — ConcurrentHashMap.merge atomic across carriers (1 & 4, +GC) = $EXP, matches real java"
