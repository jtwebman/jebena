#!/usr/bin/env bash
# Lazy-classpath smoke: compiles a small multi-class program into a directory,
# then runs it under Jebena naming ONLY the main class and pointing at the
# directory as a classpath. Jebena must lazily load the helper classes by name
# and produce the same int as real java. Exercises directory classpath loading.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-classpath-smoke
rm -rf "$OUT"; mkdir -p "$OUT"
"$JAVAC" -d "$OUT" "$ROOT"/test/classpath/*.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver cp.Main entry 2>/dev/null)
# Only the main class is named; the directory is the classpath (no .class args).
GOT=$("$JEBENA" run cp/Main entry "$OUT" "$ROOT/jbase/out" 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
echo "classpath-smoke: jebena=$GOT  java=$EXP"
[ "$GOT" = "$EXP" ] && [ -n "$GOT" ] || { echo "CLASSPATH SMOKE FAILED (mismatch)"; exit 1; }
echo "classpath-smoke: OK — lazy directory classpath loading matches real java"

# --- JAR variant: pack the same classes into a .jar and load from the archive ---
JAR="$OUT/app.jar"
JARBIN="$(command -v jar || echo "$JDK/bin/jar")"
if [ -x "$JARBIN" ] || command -v jar >/dev/null 2>&1; then
  ( cd "$OUT" && "$JARBIN" cf "$JAR" cp/*.class )
else
  # fall back to zip if jar is unavailable
  ( cd "$OUT" && zip -q -r "$JAR" cp/*.class )
fi
GOTJAR=$("$JEBENA" run cp/Main entry "$JAR" "$ROOT/jbase/out" 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
echo "classpath-smoke(jar): jebena=$GOTJAR  java=$EXP"
[ "$GOTJAR" = "$EXP" ] && [ -n "$GOTJAR" ] || { echo "CLASSPATH JAR SMOKE FAILED (mismatch)"; exit 1; }
echo "classpath-smoke(jar): OK — lazy .jar classpath loading matches real java"
