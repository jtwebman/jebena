#!/usr/bin/env bash
# Breadth differential: java.time / TreeMap / LinkedHashMap / Collections / Arrays
# checked byte-for-byte against real java. Unlike differential.sh (which runs the
# intrinsic/stub layer only), these cases exercise our clean-room jbase bytecode,
# so jbase is passed EAGERLY (explicit .class list) — a lazy directory classpath
# would let the Arrays intrinsic shadow jbase's Arrays and skip java.time entirely.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-breadth-diff
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$JAVAC" -d "$OUT" "$ROOT"/test/diff/DiffColl.java "$ROOT"/test/diff/Driver.java \
  || { echo "javac failed"; exit 1; }
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/*.class | grep -v '/Driver.class')

CASES="ldPlus ldMinus ldLeap ldDayOfYear ldPlusMonths periodDays periodNeg ldtCombine \
durMinutes durCompare ltPlus \
treeOrder treeNav lhmOrder collSort collMaxMin collReverse arrSortSearch arrCopyRange"

pass=0; fail=0
printf "%-16s %12s %12s   %s\n" CASE JAVA JEBENA RESULT
for m in $CASES; do
  jv=$("$JAVA" -cp "$OUT" Driver DiffColl "$m" 2>/dev/null)
  jb=$(timeout 30 "$JEBENA" run DiffColl "$m" "${APP[@]}" "${JBASE[@]}" 2>&1 \
        | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
  if [ "$jv" = "$jb" ]; then printf "%-16s %12s %12s   OK\n" "$m" "$jv" "$jb"; pass=$((pass+1));
  else printf "%-16s %12s %12s   MISMATCH\n" "$m" "$jv" "$jb"; fail=$((fail+1)); fi
done
echo "----"
echo "breadth-diff: $pass passed, $fail mismatched"
[ "$fail" -eq 0 ]
