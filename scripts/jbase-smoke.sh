#!/usr/bin/env bash
# Differential smoke for Jebena's OWN clean-room java.base: runs JBaseSmoke.demo on
# real java (real java.base) and on Jebena (our jbase/ loaded as real bytecode), and
# checks they agree. The driver is written to avoid identity-hash *values* (only
# stable-equality), so the two runtimes must produce the same int.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-jbase-smoke
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/jbase/JBaseSmoke.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver jebena.JBaseSmoke demo 2>/dev/null)
JBASE_CLASSES=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
GOT=$("$JEBENA" run jebena/JBaseSmoke demo "$OUT/jebena/JBaseSmoke.class" $JBASE_CLASSES 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
echo "jbase-smoke: jebena=$GOT  java=$EXP"
[ "$GOT" = "$EXP" ] || { echo "JBASE SMOKE FAILED (mismatch)"; exit 1; }
echo "jbase-smoke: OK — our clean-room java.base matches real java on Object/Math/Throwable/String"
