#!/usr/bin/env bash
# Differential testing: run each method on real `java` and on Jebena, compare.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-diff
rm -rf "$OUT"; mkdir -p "$OUT"

javac -d "$OUT" "$ROOT"/test/diff/DiffTest.java "$ROOT"/test/diff/Driver.java || { echo "javac failed"; exit 1; }
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

METHODS="arith loops rec longMath doubleMath floatMath bits arrays sw gcd conv shifts exc idivEdge overflow"
pass=0; fail=0
printf "%-12s %12s %12s   %s\n" METHOD JAVA JEBENA RESULT
for m in $METHODS; do
  jv=$(java -cp "$OUT" Driver DiffTest "$m" 2>/dev/null)
  jb=$("$JEBENA" run DiffTest "$m" "$OUT/DiffTest.class" 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
  if [ "$jv" = "$jb" ]; then printf "%-12s %12s %12s   OK\n" "$m" "$jv" "$jb"; pass=$((pass+1));
  else printf "%-12s %12s %12s   MISMATCH\n" "$m" "$jv" "$jb"; fail=$((fail+1)); fi
done
echo "----"
echo "differential: $pass passed, $fail mismatched"
[ "$fail" -eq 0 ]
