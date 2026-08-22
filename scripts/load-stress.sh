#!/usr/bin/env bash
# Concurrent class-loading + interning stress. 8 fibers race to first-load 12
# classes (st.gen.C0..C11) from a classpath DIRECTORY (lazy load) and first-intern
# their distinct string literals. The reentrant, safepoint-polling load_lock must
# serialize lazy load/register/getMirror/intern/<clinit> across carriers with no
# duplicate load, no torn loader arrays, no double init -- and every fiber still
# computes the same value. Runs at 1 & 4 carriers, and at 4 carriers WITH GC
# forced (loading allocates: mirrors, literals, instances), all exact vs real java.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-load; SRC=/tmp/jebena-load-src
rm -rf "$OUT" "$SRC"; mkdir -p "$OUT" "$SRC/st/gen"
# Generate the 12 lazily-loaded classes, each with a DISTINCT 2-char string literal.
for i in $(seq 0 11); do
  cat > "$SRC/st/gen/C$i.java" <<EOF
package st.gen;
public class C$i {
    public static int v() { return "L$i".length() + $i; }
}
EOF
done
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
# Compile the generated classes into a classpath DIR (loaded lazily by jebena), and
# LoadStress separately (its main class is provided; C0..C11 are found on the dir).
"$JAVAC" -d "$OUT" "$SRC"/st/gen/*.java
"$JAVAC" -cp "$OUT" -d "$OUT" "$ROOT"/test/stress/LoadStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.LoadStress demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
# LoadStress class file passed explicitly; the st/gen dir is a classpath entry so
# C0..C11 load lazily on first use (concurrently, on the worker carriers).
LS="$OUT/st/LoadStress.class"
fail=0
check() { # $1 label  $2 env
  for rep in 1 2 3 4; do
    ALL=$(timeout 40 bash -c "JEBENA_CARRIER_TRACE=1 $2 '$JEBENA' run st/LoadStress demo $LS $OUT $JBASE" 2>&1)
    [ $? -eq 124 ] && { echo "load-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "load-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1"
check "carriers=4" "JEBENA_CARRIERS=4"
check "carriers=4+GC" "JEBENA_CARRIERS=4 JEBENA_GC_INTERVAL=200"
[ "$fail" = 0 ] || exit 1
echo "load-stress: OK — 8 fibers concurrently first-load 12 classes + intern = $EXP (carriers 1 & 4, +GC), matches real java"
