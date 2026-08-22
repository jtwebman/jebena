#!/usr/bin/env bash
# Green-thread stress: many fibers incrementing a shared AtomicInteger, joined by
# main; jebena must match real java exactly. Runs at the default 1 carrier and at
# JEBENA_CARRIERS=4 (real parallel carriers when 4d-4-iii lands; today the extra
# carriers aren't spawned yet, so both are the cooperative path — either way the
# total must be exact). Repeated to catch nondeterminism.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-thread-stress
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/*.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
EXP=$("$JAVA" -cp "$OUT" Driver st.StressMain demo 2>/dev/null)
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
for carriers in 1 4; do
  for rep in 1 2 3; do
    GOT=$(JEBENA_CARRIERS=$carriers "$JEBENA" run st/StressMain demo $APP $JBASE 2>&1 | sed -n 's/.*= \(-\?[0-9]*\).*/\1/p')
    if [ "$GOT" != "$EXP" ]; then echo "thread-stress: FAIL carriers=$carriers rep=$rep jebena=$GOT java=$EXP"; fail=1; fi
  done
done
[ "$fail" = 0 ] || exit 1
echo "thread-stress: OK — 8 fibers x1000 incrementAndGet = $EXP (carriers 1 & 4, matches real java)"
