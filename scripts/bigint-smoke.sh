#!/usr/bin/env bash
# A BigInteger factorial/Fibonacci/power table printer: exact arbitrary-precision
# values for n=0..25 in right-justified String.format columns, plus 50!, fib(100),
# 2^256, and digits(100!). Exercises BigInteger add/multiply/pow/valueOf/toString +
# String.format width + loops end-to-end. Full stdout byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-bigint
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/BigIntTable.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" BigIntTable)"
got="$(timeout 60 "$JEBENA" run BigIntTable main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "bigint-smoke: OK (BigInteger table + big values byte-identical to real java)"
  exit 0
else
  echo "bigint-smoke: MISMATCH"
  diff <(printf '%s\n' "$want") <(printf '%s\n' "$got") | head -20
  exit 1
fi
