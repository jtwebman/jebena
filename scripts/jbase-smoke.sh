#!/usr/bin/env bash
# Proves Jebena loads and runs its OWN clean-room java.base bytecode: compiles
# jbase/, compiles a driver, runs it on Jebena with our real java.lang.* classes
# overriding the built-in stubs / intrinsics, and checks the result.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-17-openjdk-amd64}"
OUT=/tmp/jebena-jbase-smoke
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JDK/bin/javac" -d "$OUT" "$ROOT/test/jbase/JBaseSmoke.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JBASE_CLASSES=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
GOT=$("$JEBENA" run jebena/JBaseSmoke demo "$OUT/jebena/JBaseSmoke.class" $JBASE_CLASSES 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
echo "jbase-smoke: got '$GOT' (expected 135)"
[ "$GOT" = "135" ] || { echo "JBASE SMOKE FAILED"; exit 1; }
echo "jbase-smoke: OK — Jebena ran our own clean-room java.lang.{Object,Math}"
