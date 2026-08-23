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
  "$ROOT"/test/diff/DiffOdt.java "$ROOT"/test/diff/DiffYear.java "$ROOT"/test/diff/DiffMonthDay.java "$ROOT"/test/diff/DiffAtomIntArr.java "$ROOT"/test/diff/DiffAtomLongArr.java "$ROOT"/test/diff/DiffColl3.java "$ROOT"/test/diff/DiffStream4.java "$ROOT"/test/diff/DiffMath3.java "$ROOT"/test/diff/DiffCollections2.java "$ROOT"/test/diff/DiffRegex4.java \
  "$ROOT"/test/diff/DiffScanner2.java "$ROOT"/test/diff/DiffDuration.java "$ROOT"/test/diff/DiffPeriod.java "$ROOT"/test/diff/DiffTreeNav.java "$ROOT"/test/diff/DiffNumFmt.java "$ROOT"/test/diff/DiffArrays2.java "$ROOT"/test/diff/DiffIntStream2.java "$ROOT"/test/diff/DiffOptional2.java "$ROOT"/test/diff/DiffRegex5.java "$ROOT"/test/diff/DiffUnsigned.java \
  "$ROOT"/test/diff/DiffInstant.java "$ROOT"/test/diff/DiffClock.java "$ROOT"/test/diff/DiffAtomRefArr.java "$ROOT"/test/diff/DiffColl4.java "$ROOT"/test/diff/DiffCollectorOf.java "$ROOT"/test/diff/DiffSplitRnd.java "$ROOT"/test/diff/DiffObjects.java "$ROOT"/test/diff/DiffAtomUpd.java "$ROOT"/test/diff/DiffMath4.java "$ROOT"/test/diff/DiffLDStream.java \
  "$ROOT"/test/diff/DiffCmp2.java "$ROOT"/test/diff/DiffCLQ.java "$ROOT"/test/diff/DiffCOW.java "$ROOT"/test/diff/DiffOffsetTime.java "$ROOT"/test/diff/DiffFmtSci.java "$ROOT"/test/diff/DiffRndStream.java "$ROOT"/test/diff/DiffStreamGen.java "$ROOT"/test/diff/DiffCollections3.java "$ROOT"/test/diff/DiffOptPrim.java "$ROOT"/test/diff/DiffIntStream3.java \
  "$ROOT"/test/diff/DiffLru.java "$ROOT"/test/diff/DiffArrays3.java "$ROOT"/test/diff/DiffFmtGrp.java "$ROOT"/test/diff/DiffRegex6.java "$ROOT"/test/diff/DiffB64Mime.java "$ROOT"/test/diff/DiffStr3.java "$ROOT"/test/diff/DiffMath5.java "$ROOT"/test/diff/DiffIntRadix.java "$ROOT"/test/diff/DiffColl5.java "$ROOT"/test/diff/DiffADq2.java \
  "$ROOT"/test/diff/DiffMref.java \
  "$ROOT"/test/diff/DiffGbc.java "$ROOT"/test/diff/DiffCLD.java "$ROOT"/test/diff/DiffFmt2.java "$ROOT"/test/diff/DiffBitSet3.java "$ROOT"/test/diff/DiffArrays4.java "$ROOT"/test/diff/DiffRegex7.java "$ROOT"/test/diff/DiffB64b.java "$ROOT"/test/diff/DiffStr4.java "$ROOT"/test/diff/DiffIntU2.java "$ROOT"/test/diff/DiffCast.java \
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
ODT="toStringOffset toStringUtc toStringHms offsetSeconds epochSecondUtc epochSecondOffset getters dateParts equalsCase nanoZero"
YEAR="yearLeap2024 yearLeap2026 yearLen2026 yearLen2024 yearPlus yearMinus yearAtDay yearToString ymFebNormal ymFebLeap ymLenYear ymPlusRoll ymMonthValue ymMinusRoll ymEndOfMonth ymToString"
MONTHDAY="toStringBasic monthValueDay febValidLeap febValidNonLeap compareSign atYearDayOfYear ofMonthEnum atYearFebNonLeap equalsHash getMonthName"
ATOMINT="allZero setGet incTwice addGet getAndSetOld casSuccess casFail fromArrayCopy getAddIncDec lengthCase toStr"
ATOMLONG="length fromArray addAndGetBig getAndAdd incrementAndGet decrementAndGet getAndSet casSuccess casFail getAndIncDec toStringCk"
COLL3="toMapMergeSum toMapMergeKey1 toMapMergeKey2 toMapMergeCount minByInt maxByInt teeingSumCount teeingMinMax"
STREAM4="collectSize collectChecksum collectEmpty collectStringBuilder collectMapped toArrayLength toArrayChecksum toArrayEmpty"
MATH3="rintHalfEven rintNonTie getExponent scalbInt scalbFrac nextAfterLowBits nextAfterDown nextAfterZero floorDivMod"
COLLECTIONS2="umListSizeGet umListMutateThrows umSetMutateThrows umMapMutateThrows binarySearchComparator maxReverse minReverse disjointTrueFalse emptySetMapSize addAllSize fillCheck singletonSize"
REGEX4="splitDefaultJoin splitDefaultCount splitLimit2Join splitLimit2Count splitNegCount splitNegJoin splitRegexJoin appendReplaceResult appendReplaceLen appendReplaceGroupRef"
SCANNER2="boolTwice boolMixed delimComma delimSemicolonInts hasNextDigits nextLetters resetToWhitespace hasNextNoBool"
DURATION="parseMinutes parseHours parseNegHours ofNanosSeconds ofNanosNano multiplied divided isNeg absPos toNanosCase toStringChecksum"
PERIOD="parseYMD parseNegYear parseNegYearMonth parseWeeks parseWeekUnit multipliedByTotal negatedYears totalMonths roundTripToString isZeroFlag isNegativeFlag minusPlus"
NUMFMT="integerGrouped numberOneFrac numberThreeFrac numberLong maxFrac2 groupingOff minFracPad negativeGrouped zeroValue integerHalfEven"
TREENAV="ceilingEntry25 floorEntry25 higherEntry20 lowerEntry20 higherKey20 lowerKey20 pollFirstEntryKey pollFirstThenFirstKey pollLastEntryKey descendingFirst lastEntryValue firstEntryKey descendingMapFirstKey descendingMapCeiling"
ARRAYS2="deepToStringFlat deepEqualsFlatEqual deepEqualsFlatUnequal deepHashFlat hashLong hashObject streamSum"
INTSTREAM2="mapToObjCount asLongSum mapToDoubleSum flatMapSum asDoubleAvg mapToLongSum flatMapCount mapToDoubleDistinctCount"
OPTIONAL2="filterKeep filterDrop flatMapPresent flatMapEmpty orEmpty orPresent streamPresent streamEmpty"
REGEX5="digitLengths wrapMatches usePosition useEnd swapGroups groupCountReplace noMatch"
UNSIGNED="parseIntRadix parseUnsigned toUnsignedLongLow divideUnsignedInt remainderUnsignedInt compareUnsignedInt rotateLeftInt rotateRightInt toUnsignedStringInt parseLongRadix divideUnsignedLong remainderUnsignedLong compareUnsignedLong rotateLeftLong toUnsignedStringLong"
INSTANT="parseEpochSec parseNano ofEpochSecondNano plusMillisSec plusMillisNano plusNanosCarry minusMillis toStringPlain toStringMillis toStringNanos roundTripEpoch isBeforeCheck"
CLOCK="fixedMillisLowBits fixedInstantEpochSecond fixedZoneTotalSeconds smallFixedMillis fixedInstantSame nonUtcZone millisMatchesInstant negativeEpoch"
ATOMREFARR="allNull setGet getAndSetOld casSuccessIdentity casFailIdentity casNullExpect lengthCase fromArrayCopy toStr toStrWithNull"
COLLECTOROF="sbAbc sum1to5 sum3argIdentity listSize3arg emptySum product concatUpper totalChars"
COLL4="collectingAndThenSize collectingAndThenSetSize filteringEvens filteringNone filteringToList flatMappingDouble flatMappingEmpty flatMappingToList toCollectionSize toCollectionDups toCollectionSet"
SPLITRND="firstInt sumThreeLongs boundedFold doubleScaled boundedPow2 sameSeedEqual"
OBJECTS="elseNull elseNonNull elseGetNull elseGetNonNull checkIdx checkFromTo checkFromSize hashInts hashMixed toStringNull toStringNonNull reqSupplier"
ATOMUPD="updAndGet getAndUpd accAndGet getAndAcc refUpdAndGet refGetAndUpd refAccAndGet longGetAndUpd longGetAndAcc intAccMax"
MATH4="mhMax mhShift mhNeg mhBothNeg mhZero mhMinMin mhMixed mhOne"
LDSTREAM="mapToObjCount sortedLimitSum distinctSum doubleDistinctCount doubleSortedSkipSum asDoubleStreamSum mapToIntSum doubleSkipLimit doubleMapToLongSum longSkipSum"
CMP2="nullsFirst nullsLast nullsFirstReversed nullsLastReversed byLengthDesc byLengthAsc thenComparingLongTie thenComparingDoubleTie allNulls"
CLQ="fifoChecksum peekAfterOffers sizeIsEmptyTransitions removeMiddleThenOrder removeHeadAndTail containsHitMiss pollEmpty iteratorOrder toStringHash"
COW="addGetFold setThenGet removeIntSizeChecksum addIfAbsentExisting addIfAbsentNew indexOfHitMiss containsCheck iteratorSnapshotSum addAtIndex removeObject"
OFFTIME="toStringChecksum utcEndsWithZ utcChecksum hourMinute secondNano offsetTotalSeconds compareByInstant compareSameOffset equalsCheck hashConsistent"
FMTSCI="sciE sciEPrec2 sciBigE sciNeg sciPlus sciWidth genG genSmall genPrec3 genUpper"
RNDSTREAM="ints5sum ints8bounded intsNegRange intsZero longs4count longs4fold longsSum doubles5 doubles3"
STREAMGEN="iterateDouble generateCount generateSum iterateAdd3 generateLimitZeroCount iterateLimitOne iterateMapSum generateCountTen iterateThree"
COLLECTIONS3="rotateChecksum rotateNegative replaceAllChecksum replaceAllNoMatch copyChecksum binarySearchNatural binarySearchMissing shuffleChecksum reverseOrderSort"
OPTPRIM="streamPresentSum streamEmptySum ifPresentSideEffect ifPresentOrElsePresent ifPresentOrElseEmpty orElseGetDoublePresent orElseGetDoubleEmpty orElseGetInt longStreamSum orElseThrowCatch"
INTSTREAM3="anyMatchTrue anyMatchFalse allMatch allMatchFalse noneMatch findFirst findFirstEmpty peekSum forEachAcc"

LRU="accessOrderMovesToEnd insertionOrderUnchangedByGet accessOrderReputMovesToEnd insertionOrderReputKeepsPosition lruEvictsEldest lruEvictedKeyAbsent lruAccessProtectsEntry lruSizeCapped valuesFollowAccessOrder defaultNoEviction lruSequence"
ARRAYS3="rangeFill rangeFillFull rangeFillEmptyRange rangeFillBadRange rangeFillOob setAllSquares equalsRangeMatch equalsRangeDiffLength equalsRangeContentDiff copyOfRangePad intHashCode charFill longFill mismatchDiff mismatchPrefix mismatchEqual"
FMTGRP="commaInt commaIntCommaCount plusPositive plusNegative parenNegative parenPositive parenCommaIntNeg spaceForPlus spaceNegative commaFloat parenCommaFloatNeg commaLong"
REGEX6="ciMatches ciNoFlagNoMatch ciMixed multilineCaretCount noMultilineCaretCount multilineDollarCount dotallMatches noDotallNoMatch inlineI inlineM inlineS combinedCiMultiline"
B64MIME="mimeEncodedLength mimeCrCount mimeLfCount mimeFirstLineLength mimeLineLengthsOk mimeEncodedChecksum mimeRoundTripSum mimeRoundTripLength mimeDecodeIgnoresWhitespace mimeDecodeBasicString mimeShortNoBreak mimeExact76"
STR3="stripLeadingLen stripTrailingLen stripBothLen charsSum charsSumLong charsCount linesCount linesCountTrailing linesChecksum formattedLen formattedHash isBlankFlags"
MATH5="addExactInt addExactIntOverflow addExactLong addExactLongOverflow subtractExactInt subtractExactIntOverflow subtractExactLongOverflow multiplyExactInt multiplyExactIntOverflow multiplyExactLong multiplyExactLongOverflow toIntExactNormal toIntExactOverflow negateExactNormal negateExactOverflow negateExactLongOverflow incrementExactOverflow decrementExactOverflow incDecNormal"
INTRADIX="parseHex parseBinNeg parseVariousRadix toStringRadix radixStringLengths radixStringHashes bitCounts highLowBits leadingTrailingZeros reverseOps longRadix parseErrors"
COLL5="unmodListThrows unmodSetThrows unmodMapThrows frequencyCount disjointTrue disjointFalse nCopiesSum swapValues rotateChecksum rotateNegative fillList"
ADQ2="descendingChecksum descendingCount removeFirstOccurrence removeLastOccurrence removeOccurrenceMiss peekAndGet peekEmptyVsGetEmpty addFirstGrowthOrder addFirstGrowthEndpoints offerInterleave offerInterleaveDescending removeLastOccurrenceAtHead"
MREF="sumBinOp sumBiFunc maxBinOp minBinOp longSumBinOp longMaxBinOp longMinBinOp absFunc strLenToInt mergeSum mergeAccumulateValues"
GBC="gbcClassifierSizes gbcCounting groupingByMapping groupingByMappingSum partitioningSizes partitioningCounting partitioningEmptyBucket groupingByTreeMapKeys groupingByTreeMapCounting reducingProduct reducingSum"
CLD="offerLastPollFirstFifo offerFirstPollFirstLifo offerLastPollLastLifo peekFirstLast addFirstAddLastSize descendingIteratorChecksum iteratorForwardChecksum pollEmpty containsHitMiss removeFirstOccurrence removeLastOccurrence pushPopStack"
FMT2="plusZeroWidth parenZeroNeg plusComma spaceComma leftWidth plusPrecF parenPrecFNeg zeroWidthPrecF plusSci commaWidth parenZeroNegWide spacePlusComma"
BITSET3="getSubsetAligned getSubsetUnaligned getSubsetBeyondLength setRangeMultiWord clearRangeSpanning flipRangeTwice streamSumSparse streamMapReduce clearBitNavigation setBitNavigation lengthAfterFlips toStringEncoded"
ARRAYS4="setAllLongSum setAllDoubleChecksum fillDoubleRangeChecksum hashCodeStringArray hashCodeStringArrayWithNull hashCodeLongArray equalsLongArrays fillBooleanCountTrues copyOfIntGrowShrink"
REGEX7="splitPosLimit splitPosLimitBig splitZeroLimit splitDefault splitNegLimit groupCountNested nestedGroupLens groupStartEnd findGroupBounds replaceBackref replaceSwapPairs"
B64B="basicWithoutPad basicWithPad urlWithoutPad urlDefault mimeCustomLines mimeCustomChecksum mimeLineRounding mimeZeroLine mimeIllegalSeparator encodeBytesChecksum decodeNoPad roundTripBasic roundTripUrlNoPad"
STR4="repeatLenHash repeatZero indentLenLines indentNegative indentEmpty stripIndentBasic stripIndentMixed stripIndentNoTrailing codePointsCount codePointsSum codePointsMapped transformLength transformUpper"
INTU2="parseMaxAsBits parseMaxBitCount parseUnsignedRadixHex parseUnsignedRadixBin toUnsignedStringLenHash toUnsignedStringHex255 toUnsignedStringNeg1Hex divRemUnsignedInt toUnsignedLongLowBits toUnsignedLongHighZero parseUnsignedLongMax parseUnsignedLongBitCount parseUnsignedLongRadixHex parseUnsignedLongMid longToUnsignedStringLenHash longToUnsignedStringHex longToUnsignedStringOct longDivRemUnsigned roundTripLong parseMinusSentinel parseTooBigIntSentinel"
CAST="treeMapInstanceof treeMapCastThenUse hashMapInstanceof concurrentHashMapInstanceof arrayListInstanceof arrayListCastThenUse falseCases nullInstanceof"
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
for m in $ODT; do CASES="$CASES DiffOdt:$m"; done
for m in $YEAR; do CASES="$CASES DiffYear:$m"; done
for m in $MONTHDAY; do CASES="$CASES DiffMonthDay:$m"; done
for m in $ATOMINT; do CASES="$CASES DiffAtomIntArr:$m"; done
for m in $ATOMLONG; do CASES="$CASES DiffAtomLongArr:$m"; done
for m in $COLL3; do CASES="$CASES DiffColl3:$m"; done
for m in $STREAM4; do CASES="$CASES DiffStream4:$m"; done
for m in $MATH3; do CASES="$CASES DiffMath3:$m"; done
for m in $COLLECTIONS2; do CASES="$CASES DiffCollections2:$m"; done
for m in $REGEX4; do CASES="$CASES DiffRegex4:$m"; done
for m in $SCANNER2; do CASES="$CASES DiffScanner2:$m"; done
for m in $DURATION; do CASES="$CASES DiffDuration:$m"; done
for m in $PERIOD; do CASES="$CASES DiffPeriod:$m"; done
for m in $NUMFMT; do CASES="$CASES DiffNumFmt:$m"; done
for m in $TREENAV; do CASES="$CASES DiffTreeNav:$m"; done
for m in $ARRAYS2; do CASES="$CASES DiffArrays2:$m"; done
for m in $INTSTREAM2; do CASES="$CASES DiffIntStream2:$m"; done
for m in $OPTIONAL2; do CASES="$CASES DiffOptional2:$m"; done
for m in $REGEX5; do CASES="$CASES DiffRegex5:$m"; done
for m in $UNSIGNED; do CASES="$CASES DiffUnsigned:$m"; done
for m in $INSTANT; do CASES="$CASES DiffInstant:$m"; done
for m in $CLOCK; do CASES="$CASES DiffClock:$m"; done
for m in $ATOMREFARR; do CASES="$CASES DiffAtomRefArr:$m"; done
for m in $COLLECTOROF; do CASES="$CASES DiffCollectorOf:$m"; done
for m in $COLL4; do CASES="$CASES DiffColl4:$m"; done
for m in $SPLITRND; do CASES="$CASES DiffSplitRnd:$m"; done
for m in $OBJECTS; do CASES="$CASES DiffObjects:$m"; done
for m in $ATOMUPD; do CASES="$CASES DiffAtomUpd:$m"; done
for m in $MATH4; do CASES="$CASES DiffMath4:$m"; done
for m in $LDSTREAM; do CASES="$CASES DiffLDStream:$m"; done
for m in $CMP2; do CASES="$CASES DiffCmp2:$m"; done
for m in $CLQ; do CASES="$CASES DiffCLQ:$m"; done
for m in $COW; do CASES="$CASES DiffCOW:$m"; done
for m in $OFFTIME; do CASES="$CASES DiffOffsetTime:$m"; done
for m in $FMTSCI; do CASES="$CASES DiffFmtSci:$m"; done
for m in $RNDSTREAM; do CASES="$CASES DiffRndStream:$m"; done
for m in $STREAMGEN; do CASES="$CASES DiffStreamGen:$m"; done
for m in $COLLECTIONS3; do CASES="$CASES DiffCollections3:$m"; done
for m in $OPTPRIM; do CASES="$CASES DiffOptPrim:$m"; done
for m in $INTSTREAM3; do CASES="$CASES DiffIntStream3:$m"; done
for m in $LRU; do CASES="$CASES DiffLru:$m"; done
for m in $ARRAYS3; do CASES="$CASES DiffArrays3:$m"; done
for m in $FMTGRP; do CASES="$CASES DiffFmtGrp:$m"; done
for m in $REGEX6; do CASES="$CASES DiffRegex6:$m"; done
for m in $B64MIME; do CASES="$CASES DiffB64Mime:$m"; done
for m in $STR3; do CASES="$CASES DiffStr3:$m"; done
for m in $MATH5; do CASES="$CASES DiffMath5:$m"; done
for m in $INTRADIX; do CASES="$CASES DiffIntRadix:$m"; done
for m in $COLL5; do CASES="$CASES DiffColl5:$m"; done
for m in $ADQ2; do CASES="$CASES DiffADq2:$m"; done
for m in $MREF; do CASES="$CASES DiffMref:$m"; done
for m in $GBC; do CASES="$CASES DiffGbc:$m"; done
for m in $CLD; do CASES="$CASES DiffCLD:$m"; done
for m in $FMT2; do CASES="$CASES DiffFmt2:$m"; done
for m in $BITSET3; do CASES="$CASES DiffBitSet3:$m"; done
for m in $ARRAYS4; do CASES="$CASES DiffArrays4:$m"; done
for m in $REGEX7; do CASES="$CASES DiffRegex7:$m"; done
for m in $B64B; do CASES="$CASES DiffB64b:$m"; done
for m in $STR4; do CASES="$CASES DiffStr4:$m"; done
for m in $INTU2; do CASES="$CASES DiffIntU2:$m"; done
for m in $CAST; do CASES="$CASES DiffCast:$m"; done

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
