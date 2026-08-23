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
"$JAVAC" -d "$OUT" "$ROOT"/test/diff/DiffColl.java "$ROOT"/test/diff/DiffRegex.java \
  "$ROOT"/test/diff/DiffStream.java "$ROOT"/test/diff/DiffString.java "$ROOT"/test/diff/DiffTrace.java "$ROOT"/test/diff/DiffBits.java "$ROOT"/test/diff/DiffFmt.java "$ROOT"/test/diff/DiffBig.java "$ROOT"/test/diff/DiffNav.java "$ROOT"/test/diff/DiffList.java "$ROOT"/test/diff/DiffMath.java "$ROOT"/test/diff/DiffChar.java \
  "$ROOT"/test/diff/Driver.java || { echo "javac failed"; exit 1; }
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/*.class | grep -v '/Driver.class')

COLL="ldPlus ldMinus ldLeap ldDayOfYear ldPlusMonths periodDays periodNeg ldtCombine \
durMinutes durCompare ltPlus \
treeOrder treeNav lhmOrder collSort collMaxMin collReverse arrSortSearch arrCopyRange"
REGEX="matchLit matchDot matchDigit matchWord matchSpace matchAlt quantStar quantPlus \
quantOpt braceExact braceRange classRange classNeg anchors nonCapturing altGroup \
dotStarGreedy findCount groupCapture startEnd groupCountTest lookingAt wordCount sumLens"
STREAM="mapFilterSum countDistinct sortedLimit intRange intRangeClosedSq mapToIntLen \
reduceMax joiningLen groupingSize skipCount findFirstEven matchFlags boxedSum toListSize"
STRING="contains repeat repeatContent isBlank strip stripEmpty join joinEmpty \
splitComma splitTrailingEmpty splitLimit splitRegex replaceSeq replaceEmptyTarget matches"
TRACE="topLine topMethod topClass lineOrder fileName"
BITS="intBitCount intNlz intNtz intHighLow intReverse hexBin longBits longHexValue sbDelete sbInsertReverse joiner joinerEmpty"
FMT="fmtD fmtWidth fmtZero fmtLeft fmtPlus fmtComma fmtCommaNeg fmtCommaLong fmtHex fmtHexAlt fmtHexZero fmtOct fmtStr fmtStrPrec fmtChar fmtBool fmtPct fmtFloat fmtMix"
BIG="addSub multiply divMod powBig factorial gcd compare signNeg modArith negDivide"
NAV="tmCeilFloor tmHigherLower tmEdges tmMisses tsNav tsEdges optMapFilter optOrElse optOrElseGet optFilterEmpty optIfPresentOrElse optOr"
LIST="dequeOps llAsList iterRemove collReverseMaxMin collHelpers arraysOps arraysEqToStr"
MATH="limits hypot round exactOk addOverflow mulOverflow floorLong setAddAll setRetainRemove setContainsAll"
CHAR="classify caseTests caseConvert whitespace digitRadix numeric"

CASES=""
for m in $COLL; do CASES="$CASES DiffColl:$m"; done
for m in $REGEX; do CASES="$CASES DiffRegex:$m"; done
for m in $STREAM; do CASES="$CASES DiffStream:$m"; done
for m in $STRING; do CASES="$CASES DiffString:$m"; done
for m in $TRACE; do CASES="$CASES DiffTrace:$m"; done
for m in $BITS; do CASES="$CASES DiffBits:$m"; done
for m in $FMT; do CASES="$CASES DiffFmt:$m"; done
for m in $BIG; do CASES="$CASES DiffBig:$m"; done
for m in $NAV; do CASES="$CASES DiffNav:$m"; done
for m in $LIST; do CASES="$CASES DiffList:$m"; done
for m in $MATH; do CASES="$CASES DiffMath:$m"; done
for m in $CHAR; do CASES="$CASES DiffChar:$m"; done

pass=0; fail=0
printf "%-24s %12s %12s   %s\n" CASE JAVA JEBENA RESULT
for cm in $CASES; do
  cls="${cm%%:*}"; m="${cm##*:}"
  jv=$("$JAVA" -cp "$OUT" Driver "$cls" "$m" 2>/dev/null)
  jb=$(timeout 30 "$JEBENA" run "$cls" "$m" "${APP[@]}" "${JBASE[@]}" 2>&1 \
        | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
  if [ "$jv" = "$jb" ]; then printf "%-24s %12s %12s   OK\n" "$cm" "$jv" "$jb"; pass=$((pass+1));
  else printf "%-24s %12s %12s   MISMATCH\n" "$cm" "$jv" "$jb"; fail=$((fail+1)); fi
done
echo "----"
echo "breadth-diff: $pass passed, $fail mismatched"
[ "$fail" -eq 0 ]
