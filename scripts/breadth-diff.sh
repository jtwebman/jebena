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
"$JAVAC" -encoding UTF-8 -d "$OUT" "$ROOT"/test/diff/DiffColl.java "$ROOT"/test/diff/DiffRegex.java \
  "$ROOT"/test/diff/DiffStream.java "$ROOT"/test/diff/DiffString.java "$ROOT"/test/diff/DiffTrace.java "$ROOT"/test/diff/DiffBits.java "$ROOT"/test/diff/DiffFmt.java "$ROOT"/test/diff/DiffBig.java "$ROOT"/test/diff/DiffNav.java "$ROOT"/test/diff/DiffList.java "$ROOT"/test/diff/DiffMath.java "$ROOT"/test/diff/DiffChar.java "$ROOT"/test/diff/DiffMap.java \
  "$ROOT"/test/diff/DiffRandom.java "$ROOT"/test/diff/DiffPQ.java "$ROOT"/test/diff/DiffTok.java "$ROOT"/test/diff/DiffVec.java "$ROOT"/test/diff/DiffStk.java "$ROOT"/test/diff/DiffUuid.java "$ROOT"/test/diff/DiffOptInt.java "$ROOT"/test/diff/DiffADq.java \
  "$ROOT"/test/diff/DiffBitSet.java "$ROOT"/test/diff/DiffScanner.java "$ROOT"/test/diff/DiffCmp.java "$ROOT"/test/diff/DiffBigDec.java "$ROOT"/test/diff/DiffCollectors.java \
  "$ROOT"/test/diff/DiffDow.java "$ROOT"/test/diff/DiffDecFmt.java "$ROOT"/test/diff/DiffPath.java "$ROOT"/test/diff/DiffIntStream.java "$ROOT"/test/diff/DiffBigDec2.java \
  "$ROOT"/test/diff/DiffDate2.java \
  "$ROOT"/test/diff/DiffB64.java "$ROOT"/test/diff/DiffCrc.java "$ROOT"/test/diff/DiffLongStream.java "$ROOT"/test/diff/DiffDoubleStream.java "$ROOT"/test/diff/DiffRegex2.java "$ROOT"/test/diff/DiffStream2.java "$ROOT"/test/diff/DiffMath2.java "$ROOT"/test/diff/DiffBigDec3.java "$ROOT"/test/diff/DiffDtf.java "$ROOT"/test/diff/DiffGauss.java \
  "$ROOT"/test/diff/DiffStrBytes.java \
  "$ROOT"/test/diff/DiffHex.java "$ROOT"/test/diff/DiffTimeUnit.java "$ROOT"/test/diff/DiffColl2.java "$ROOT"/test/diff/DiffRegex3.java "$ROOT"/test/diff/DiffZoneOffset.java "$ROOT"/test/diff/DiffBitSet2.java "$ROOT"/test/diff/DiffSB2.java "$ROOT"/test/diff/DiffStream3.java "$ROOT"/test/diff/DiffChar2.java "$ROOT"/test/diff/DiffBigInt2.java \
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
MAP="mergeCount getOrDefault putIfAbsent computeIfAbsent compute forEachSum"
RANDOM_C="newSeedInts newSeedBounded seedLong seedBools seedDouble seedFloat setSeedReset boundPowerOfTwo"
PQ="pollSorted peekMin sizeTrack containsHitMiss removeObj removeHead emptyPoll iterSum"
TOK="countWs countComma loopCount mixedDelim singleTok emptyInput changeDelim elementsApi"
VEC="addGet setElem insertAt removeAt removeObj indexOfHitMiss firstLast clearEmpty"
STK="pushPop peekTop emptyFlag searchDist sizeTrack popAll searchDup singleton"
UUID_C="versionV1 versionV4 variantBits compareSign equalsRoundTrip msbLow lsbLow toStringRoundTrip hashMix"
OPTINT="optIntGet optIntOrElse optIntPresent optLongGet optLongOrElse optDoubleOrElse optDoubleGet optEmptyFlags"
ADQ="fifo lifo bothEnds pollEnds emptyPoll sizeTrack contains offerPeek"
BITSET="setAndCardinality setRangeAndClear flipBehavior lengthAndSize nextSetAndClear andOp orOp xorOp andNotOp intersectsAndEquals toStringLen getSubset"
SCANNER="ints mixed lines emptyScanner longs doubles nextLineAfterToken whitespaceRuns negatives tabsAndNewlines"
CMP="natural reverse reversedOfNatural byAbs byLength byLengthThenNatural thenComparingIntCase byLong byDouble emptyNatural"
BIGDEC="addPlain subtractPlain multiplyPlain negativeAddPlain compareEqualValue compareGreater compareLess signumNeg scaleAfterMultiply setScaleHalfUp setScaleDown absPlain valueOfLong"
COLLECTORS="summingIntCase summingLongCase averagingIntCase averagingEmptyCase toMapCase mappingCase partitioningByCase partitioningSumCase countingCase joiningPrefixCase"
DOW="dowOfValue mondayPlus4 sundayPlus1IsMonday mondayMinus1IsSunday febLeap febNonLeap decemberPlus1IsJanuary mayFirstMonthOfQuarter fridayValue periodNormalized"
DECFMT="grouped2dpHalfEven grouped2dpNegative grouped2dpZero optionalFracRound optionalFracStrip minIntPad groupedLong oneFrac"
PATHC="fileName parent nameCount nameAt1 resolveRel resolveAbs normalizeDots normalizeRel joinMulti absAbsolute absRelative rootAbs rootRel"
INTSTREAM="filterSum reduceSum minMax average distinctSum sortedChecksum limitSum skipSum mapToObjCount"
BIGDEC2="div10by3Half div1by8Down divNeg10by3HalfEven div7by2Ceiling div7by2Floor pow25cubed pow2to10 strip movePointLeft2 movePointRight1"
DATE2="cmpSign cmpEqual beforeAfter isEqualCase dowThursday dowSunday untilDays untilMonths"
B64="encMan encMa encM encHello encEmpty roundtrip decString urlSafeChars urlRoundtrip noPad decNoPad"
CRC="crcHello adlerHello crcIncremental adlerIncremental crcSingleBytes adlerSingleBytes crcReset adlerReset crcEmpty adlerRange"
LONGSTREAM="rangeClosedSum rangeMapSquareSum ofMax ofMin filterEvenSum reduceProduct averageMicros boxedCount emptyMinPresent toArrayChecksum"
DBLSTREAM="sumBasic mapSum filterCount averageBasic averageEmpty reduceProduct minVal maxVal minEmpty boxedToArray"
REGEX2="replaceAllDigits replaceFirstDigit groupSwap groupSwapFirst quoteMatch quoteReplLiteral quoteReplMixed escapedDollar noMatch wholeRef"
STREAM2="flatMapSum flatMapEmpty concatCount concatEmpty takeWhileSum takeWhileNone dropWhileSum dropWhileAll minNat maxNat minEmpty"
MATH2="toRadians180 toDegreesPI copySign3neg1 copySignFloat signumNeg5 signumZero signumFloatPos ulp1bits nextUp1 nextDown1"
BIGDEC3="remainderBasic remainderFrac remainderNeg divToIntegral divToIntegralFrac maxOf minOf precisionOf precisionZero roundBasic roundCarry roundFloorMode"
DTF="isoDate slashDate dateTime literalHeavy midnightTime endOfDay compactNoSep singleLetters fullDateTime"
GAUSS="single42 single42Coarse sum3Seed7 sum3Seed7Coarse combineSeed1 combineSeed1Coarse cachedPairSecond reseedClearsCache"
STRBYTES="asciiLen asciiBytes twoByteLen twoByteBytes threeByteLen threeByteBytes roundtripAscii roundtripUtf8 offsetDecode emptyBytes"
HEX="formatLower formatUpper parseRoundtrip parseFormatRoundtrip toHexDigitsInt255 toHexDigitsInt1234 toHexDigitsLong toHexDigitsByte delimiterFormat delimiterRoundtrip lowHighDigit"
TIMEUNIT="secondsToMillis minutesToSeconds hoursToMinutes daysToHours millisToSeconds convertMillisToSeconds saturatingDaysToNanos minutesOrdinal negativeSaturation valueOfRoundTrip"
COLL2="caseGroupCounts caseGroupSummingInt caseSummarizing caseReducingSum caseReducingMax caseUnmodifiableSize caseAveragingLong"
REGEX3="namedY namedM namedD namedEqualsPositional startEndGroup2 startEndByName positionalStartEnd findNamed"
ZONEOFF="utcId ofHoursPos ofHoursNeg ofHoursMinutes ofHoursMinutesNeg totalSecondsId negSecondsId totalSecondsValue utcTotal maxOffset equalsCheck"
SB2="replaceMid replaceGrow replaceShrink indexOfCd indexOfFrom indexOfMiss lastIndexOfA lastIndexOfBc insertInt insertBool insertChar insertLong insertChars appendCp appendCpSupp capacityCheck"
BITSET2="valueOfCardChecksum valueOfTrailingZero valueOfMultiWord toLongArrayCheck roundTrip emptyToLongArray streamSum streamCount streamFiltered"
STREAM3="iterateCount iterateSumInt iterateSumReduce iterateEmpty mapToLongSum mapToLongWide mapToDoubleSum mapToDoubleEmpty iterateMapToDouble"
CHAR2="charCountA charCountEmoji toCharsLen toCharsChecksum codePointAtA codePointAtEmoji isSurrogateHigh isHighSurrogateTest isLowSurrogateTest isAlphabeticZ isAlphabetic5 toCodePointTest"
BIGINT2="shiftLeft20 shiftRight3 testBit2 setBit0 clearBit1 flipBit1 bitLength255 bitCount255 negBitLength andCase orCase xorCase notCase modPowCase modInverseCase prime17 composite21 sqrt144 bigShiftChecksum"

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
for m in $MAP; do CASES="$CASES DiffMap:$m"; done
for m in $RANDOM_C; do CASES="$CASES DiffRandom:$m"; done
for m in $PQ; do CASES="$CASES DiffPQ:$m"; done
for m in $TOK; do CASES="$CASES DiffTok:$m"; done
for m in $VEC; do CASES="$CASES DiffVec:$m"; done
for m in $STK; do CASES="$CASES DiffStk:$m"; done
for m in $UUID_C; do CASES="$CASES DiffUuid:$m"; done
for m in $OPTINT; do CASES="$CASES DiffOptInt:$m"; done
for m in $ADQ; do CASES="$CASES DiffADq:$m"; done
for m in $BITSET; do CASES="$CASES DiffBitSet:$m"; done
for m in $SCANNER; do CASES="$CASES DiffScanner:$m"; done
for m in $CMP; do CASES="$CASES DiffCmp:$m"; done
for m in $BIGDEC; do CASES="$CASES DiffBigDec:$m"; done
for m in $COLLECTORS; do CASES="$CASES DiffCollectors:$m"; done
for m in $DOW; do CASES="$CASES DiffDow:$m"; done
for m in $DECFMT; do CASES="$CASES DiffDecFmt:$m"; done
for m in $PATHC; do CASES="$CASES DiffPath:$m"; done
for m in $INTSTREAM; do CASES="$CASES DiffIntStream:$m"; done
for m in $BIGDEC2; do CASES="$CASES DiffBigDec2:$m"; done
for m in $DATE2; do CASES="$CASES DiffDate2:$m"; done
for m in $B64; do CASES="$CASES DiffB64:$m"; done
for m in $CRC; do CASES="$CASES DiffCrc:$m"; done
for m in $LONGSTREAM; do CASES="$CASES DiffLongStream:$m"; done
for m in $DBLSTREAM; do CASES="$CASES DiffDoubleStream:$m"; done
for m in $REGEX2; do CASES="$CASES DiffRegex2:$m"; done
for m in $STREAM2; do CASES="$CASES DiffStream2:$m"; done
for m in $MATH2; do CASES="$CASES DiffMath2:$m"; done
for m in $BIGDEC3; do CASES="$CASES DiffBigDec3:$m"; done
for m in $DTF; do CASES="$CASES DiffDtf:$m"; done
for m in $GAUSS; do CASES="$CASES DiffGauss:$m"; done
for m in $STRBYTES; do CASES="$CASES DiffStrBytes:$m"; done
for m in $HEX; do CASES="$CASES DiffHex:$m"; done
for m in $TIMEUNIT; do CASES="$CASES DiffTimeUnit:$m"; done
for m in $COLL2; do CASES="$CASES DiffColl2:$m"; done
for m in $REGEX3; do CASES="$CASES DiffRegex3:$m"; done
for m in $ZONEOFF; do CASES="$CASES DiffZoneOffset:$m"; done
for m in $SB2; do CASES="$CASES DiffSB2:$m"; done
for m in $BITSET2; do CASES="$CASES DiffBitSet2:$m"; done
for m in $STREAM3; do CASES="$CASES DiffStream3:$m"; done
for m in $CHAR2; do CASES="$CASES DiffChar2:$m"; done
for m in $BIGINT2; do CASES="$CASES DiffBigInt2:$m"; done

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
