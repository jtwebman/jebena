#!/usr/bin/env bash
# Collections + nested-class stress. 8 worker fibers each build/drain a
# LinkedList and an ArrayDeque (ring-buffer growth + nested ArrayDeque$Itr) and
# walk a self-referential nested-class chain -- exercising nested-class
# instantiation (LinkedList$Node, ArrayDeque$Itr, our own Node) through the real
# class-load path, plus concurrent allocation + GC. Deterministic: 13280.
#
# NOTE: this script passes jbase/out and the app OUT as classpath DIRECTORIES,
# not an expanded file glob. jebena loads classes lazily by name from a dir, so
# nested classes whose files contain '$' load correctly. (A file glob run through
# a nested `bash -c "... $FILES"` would have '$Node'/'$Itr' expanded away by the
# inner shell -- a harness trap that silently drops nested .class files.)
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-coll
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/CollFuzz.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"
EXP=$("$JAVA" -cp "$OUT" Driver st.CollFuzz demo 2>/dev/null)
fail=0
check() { # $1 label  $2 carriers-env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 40 env $2 "$JEBENA" run st/CollFuzz demo "$OUT" "$JB" 2>&1)
    [ $? -eq 124 ] && { echo "coll-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "coll-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 3
check "carriers=4" "JEBENA_CARRIERS=4" 12
check "carriers=4+GC" "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "coll-stress: OK — LinkedList + ArrayDeque + nested-class instantiation (LinkedList\$Node/ArrayDeque\$Itr/Node), 8 fibers = $EXP (carriers 1 & 4, +GC), matches real java"
