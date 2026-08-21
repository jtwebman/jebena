#!/usr/bin/env bash
# stdout differential: run HelloWorld.run() on real java and on Jebena (with our
# clean-room java.base), and require identical stdout — proving System.out.println.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-out-smoke
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/jbase/HelloWorld.java" "$ROOT/test/jbase/OutDriver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JBASE_CLASSES=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
"$JAVA" -cp "$OUT" jebena.OutDriver jebena.HelloWorld run > "$OUT/java.txt" 2>/dev/null
"$JEBENA" run jebena/HelloWorld run "$OUT/jebena/HelloWorld.class" $JBASE_CLASSES > "$OUT/jebena.txt" 2>/dev/null
if diff -q "$OUT/java.txt" "$OUT/jebena.txt" >/dev/null; then
  echo "output-smoke: OK — stdout identical to real java ($(wc -l < "$OUT/java.txt") lines)"
else
  echo "output-smoke: MISMATCH"; diff "$OUT/java.txt" "$OUT/jebena.txt" | head -20; exit 1
fi
