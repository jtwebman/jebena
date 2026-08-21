#!/usr/bin/env bash
# Differential testing: run each <Class>:<method> on real `java` and Jebena, compare.
# Oracle JDK must be >= 19: java.lang.{Double,Float}.toString uses the shortest
# round-trip algorithm (Giulietti, JDK-4511638) that the Java SE spec now mandates.
# JDK 17 ships the legacy FloatingDecimal, which emits non-shortest forms for some
# values and is therefore NOT spec-conformant for float->String. Jebena targets SE25.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
# javac only needs to emit classfiles the oracle+Jebena both read; any installed one works.
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-diff
rm -rf "$OUT"; mkdir -p "$OUT"

"$JAVAC" -d "$OUT" "$ROOT"/test/diff/*.java || { echo "javac failed"; exit 1; }
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
CLASSES=$(ls "$OUT"/*.class | grep -v '/Driver.class' | tr '\n' ' ')

CASES="DiffTest:arith DiffTest:loops DiffTest:rec DiffTest:longMath DiffTest:doubleMath \
DiffTest:floatMath DiffTest:bits DiffTest:arrays DiffTest:sw DiffTest:gcd DiffTest:conv \
DiffTest:shifts DiffTest:exc DiffTest:idivEdge DiffTest:overflow DiffTest:negMod DiffTest:longEdge DiffTest:shiftBig DiffTest:dSpecial DiffTest:fSpecial DiffTest:deepRec DiffTest:charMath DiffTest:ternary DiffTest:d2lConv DiffTest:mixArith DiffTest:cmpChain DiffTest:lcmpTest DiffTest:multiArr \
OopTest:poly OopTest:vecs OopTest:allocStress OopTest:listBuild DiffMore:nanCmp DiffMore:nanCmpF DiffMore:shortCircuit DiffMore:orCircuit DiffMore:labeledBreak DiffMore:labeledCont DiffMore:doWhile DiffMore:mutual DiffMore:ack DiffMore:prePost DiffMore:compound DiffMore:longDivEdge DiffMore:negAbs DiffMore:allConv DiffMore:nestedExc DiffMore:rethrow DiffMore:gcExc DiffMore:deepMutual DiffMore:divZero DiffMore:divZeroLong DiffMore:npe DiffMore:aioobe DiffMore:aioobeNeg DiffMore:negArr DiffMore:catchSuper DiffMore:catchThrowable DiffMore:finallyOnExc DiffMore:multiCatch DiffMore:gcArith DiffMore:mAbs DiffMore:mMaxMin DiffMore:mSqrt DiffMore:mPow DiffMore:mRound DiffMore:mFloorCeil DiffMore:mHypot DiffMore:mExp DiffMore:mMixed DiffMore:copy DiffMore:copyOverlap DiffMore:copyGrow DiffMore:copyLong DiffMore:iBits DiffMore:iBits2 DiffMore:iRotate DiffMore:iReverse DiffMore:lBits DiffMore:iMinMax DiffMore:bitLoop DiffMore:sortTest DiffMore:sortBig DiffMore:fillCopy DiffMore:eqTest DiffMore:strLen DiffMore:strChar DiffMore:strHash DiffMore:strEq DiffMore:strCmp DiffMore:strLoop DiffMore:strUnicode DiffMore:strSub DiffMore:strIndex DiffMore:strPrefix DiffMore:strConcat DiffMore:strReplace DiffMore:sbBasic DiffMore:sbLoop DiffMore:sbChain DiffMore:sbInit DiffMore:sbLong DiffMore:autobox DiffMore:boxEq DiffMore:longBox DiffMore:boolBox DiffMore:doubleBox DiffMore:charBox DiffMore:mixBox DiffMore:parseBox DiffMore:boxHashSum DiffMore:fmtD1 DiffMore:fmtD2 DiffMore:fmtD3 DiffMore:fmtF1 DiffMore:fmtNeg DiffMore:fmtSpecial DiffMore:fmtLoop DiffMore:fmtToStr DiffMore:fmtPowLoop DiffMore:sortCmp DiffMore:sortRev DiffMore:sortRef DiffMore:sortStr DiffMore:sortByLen DiffMore:sortBigC DiffMore:upLow DiffMore:trimTest DiffMore:eqIgnore DiffMore:idxTest DiffMore:repeatTest DiffMore:charArr DiffMore:charClass DiffMore:charConv DiffMore:charDig DiffMore:strManip DiffMore:concatInt DiffMore:concatMix DiffMore:concatLoop DiffMore:concatStr DiffMore:concatNeg LongFields:mixed LongFields:longField LongFields:arrayOfLongFields LambdaTest:useLambda LambdaTest:captureLambda LambdaTest:biLambda LambdaTest:runnableTest LambdaTest:methodRef LambdaTest:chainLambda LambdaTest:nestedLambda LambdaTest:boundRef LambdaTest:thisCapture LambdaTest:thisCaptureBi LambdaTest:boundRefLoop"

pass=0; fail=0
printf "%-22s %14s %14s   %s\n" CASE JAVA JEBENA RESULT
for cm in $CASES; do
  cls="${cm%%:*}"; m="${cm##*:}"
  jv=$("$JAVA" -cp "$OUT" Driver "$cls" "$m" 2>/dev/null)
  jb=$("$JEBENA" run "$cls" "$m" $CLASSES 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
  if [ "$jv" = "$jb" ]; then printf "%-22s %14s %14s   OK\n" "$cm" "$jv" "$jb"; pass=$((pass+1));
  else printf "%-22s %14s %14s   MISMATCH\n" "$cm" "$jv" "$jb"; fail=$((fail+1)); fi
done
echo "----"
echo "differential: $pass passed, $fail mismatched"
[ "$fail" -eq 0 ]
