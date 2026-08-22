#!/usr/bin/env bash
# A recursive-descent integer calculator (tokenize + parse + eval; +-*/ and parens;
# unary minus; a divide-by-zero caught as ArithmeticException). Exercises recursion,
# char/String ops, Integer.parseInt/substring, integer arithmetic, and — critically —
# a VM-trap exception (idiv by zero) propagating ACROSS frames to a catch in main,
# with the "/ by zero" message. Full stdout must be byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-calc
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/Calc.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" Calc)"
got="$(timeout 60 "$JEBENA" run Calc main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "calc-smoke: OK (recursive-descent calculator + cross-frame divide-by-zero, byte-identical to real java)"
  exit 0
else
  echo "calc-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
