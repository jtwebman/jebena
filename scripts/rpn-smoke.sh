#!/usr/bin/env bash
# A Reverse-Polish-Notation integer evaluator using ArrayDeque as a stack.
# Exercises ArrayDeque push/pop/size, String.split, Integer.parseInt/valueOf,
# integer arithmetic, and three caught exception paths (ArithmeticException from a
# cross-frame divide-by-zero, IllegalStateException for underflow/leftover). Full
# stdout must be byte-identical to real java. jbase EAGER.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-rpn
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/Rpn.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" Rpn)"
got="$(timeout 60 "$JEBENA" run Rpn main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "rpn-smoke: OK (RPN evaluator over ArrayDeque + caught errors, byte-identical to real java)"
  exit 0
else
  echo "rpn-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
