#!/usr/bin/env bash
# Concurrent structural mutation of a shared ArrayList guarded by a user monitor:
# 8 fibers each add() 250 distinct values inside synchronized(lock){}. Exercises
# monitorenter/monitorexit on a user Object, ArrayList growth (backing-array realloc)
# under the lock, and the moving GC relocating that array while fibers block.
# Distinct values -> a lost add or corrupted element breaks the sum. size=2000,
# sum=7,249,000 -> demo()=7,251,000. Must match real java at carriers 1 & 4 (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-sl
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/SyncListStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.SyncListStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 45 env $1 "$JEBENA" run st/SyncListStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "synclist-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "synclist-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 3
check "JEBENA_CARRIERS=4" 8
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 5
[ "$fail" = 0 ] || exit 1
echo "synclist-stress: OK — shared ArrayList under a user monitor across carriers (1 & 4, +GC) = $EXP, matches real java"
