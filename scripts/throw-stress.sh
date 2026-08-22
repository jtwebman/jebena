#!/usr/bin/env bash
# Exception + concurrent-GC stress. 8 fibers throw+catch a freshly-allocated
# RuntimeException every other iteration while allocating arrays; with a small
# JEBENA_GC_INTERVAL the moving collector fires across carriers while exceptions
# are constructed/thrown/caught. Validates that exception handling and the moving
# GC coexist under real parallelism, and that every carrier's in-flight exception
# state is GC-rooted (per-carrier budget.pending). Deterministic: 88000.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-throw
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/ThrowStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.ThrowStress demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 40 bash -c "$2 '$JEBENA' run st/ThrowStress demo $APP $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "throw-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "throw-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 3
check "carriers=4+GC" "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 10
[ "$fail" = 0 ] || exit 1
echo "throw-stress: OK — 8 fibers throw/catch + alloc under concurrent GC = $EXP (carriers 1 & 4), matches real java"
