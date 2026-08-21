#!/usr/bin/env bash
# Compile Jebena's own clean-room java.base (jbase/) to bytecode in jbase/out.
# We author classes inside java.* packages, so javac needs --patch-module to let
# our sources stand in for the platform module during compilation. javac is only
# a compiler here; the SOURCE under jbase/ is entirely ours (clean-room).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-17-openjdk-amd64}"
JAVAC="$JDK/bin/javac"
SRC="$ROOT/jbase"
OUT="$ROOT/jbase/out"
rm -rf "$OUT"; mkdir -p "$OUT"
# All java.* sources compile as a patch of java.base.
mapfile -t JAVA_SRCS < <(find "$SRC/java" -name '*.java' 2>/dev/null)
if [ "${#JAVA_SRCS[@]}" -gt 0 ]; then
  "$JAVAC" --patch-module java.base="$SRC" -d "$OUT" "${JAVA_SRCS[@]}"
fi
echo "built jbase -> $OUT ($(find "$OUT" -name '*.class' | wc -l) classes)"
