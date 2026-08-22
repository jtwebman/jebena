#!/usr/bin/env bash
# Fiber-parking scale test: 16 joiner fibers each Thread.join() one long worker,
# then add 1; main joins all 16. Real parking makes the 16 blocked joiners yield
# their carriers (worker + others keep running) -- under the old spin-wait, 16
# blocked fibers on 4 carriers would deadlock. Deterministic: 1016. Runs at
# carriers 1 & 4 and with GC forced, timeout-guarded.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-join
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/JoinStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.JoinStress demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
check() { # $1 label $2 env $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 40 bash -c "$2 '$JEBENA' run st/JoinStress demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "join-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "join-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 3
check "carriers=4" "JEBENA_CARRIERS=4" 10
check "carriers=4+GC" "JEBENA_GC_INTERVAL=500 JEBENA_CARRIERS=4" 5
[ "$fail" = 0 ] || exit 1
echo "join-stress: OK — 16 joiners park on 1 worker (16 > 4 carriers) = $EXP (carriers 1 & 4, +GC), matches real java"
