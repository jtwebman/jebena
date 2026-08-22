#!/usr/bin/env bash
# ArrayDeque growth + LinkedBlockingQueue (BlockingQueue) tests. ArrayDequeStress
# exercises the ring-buffer growth/wrap (regression for the doubleCapacity bug that
# lost all elements when full). QueueStress is a 4-producer/4-consumer blocking
# queue where consumers PARK on the empty queue -- exact 400 at 1 & 4 carriers (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-queue
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/ArrayDequeStress.java "$ROOT"/test/stress/QueueStress.java "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JBASE=$(find "$ROOT/jbase/out" -name '*.class' | tr '\n' ' ')
APP=$(ls "$OUT"/st/*.class | tr '\n' ' ')
fail=0
# ArrayDeque growth/wrap (deterministic, single carrier is enough).
for m in demo grow; do
  EXP=$("$JAVA" -cp "$OUT" Driver st.ArrayDequeStress "$m" 2>/dev/null)
  GOT=$(timeout 20 bash -c "'$JEBENA' run st/ArrayDequeStress $m $APP $JBASE" 2>&1 | sed -n "s/.*$m() = \(-\?[0-9]*\).*/\1/p")
  [ "$GOT" = "$EXP" ] || { echo "queue-stress: FAIL ArrayDeque $m jebena=$GOT java=$EXP"; fail=1; }
done
# LinkedBlockingQueue producer/consumer (consumers park on empty).
QEXP=$("$JAVA" -cp "$OUT" Driver st.QueueStress demo 2>/dev/null)
qcheck() { for rep in $(seq 1 "$2"); do ALL=$(timeout 40 bash -c "$1 '$JEBENA' run st/QueueStress demo $APP $JBASE" 2>&1); [ $? -eq 124 ] && { echo "queue-stress: FAIL QueueStress $1 HANG"; fail=1; }; G=$(printf '%s\n' "$ALL"|sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p'); [ "$G" = "$QEXP" ] || { echo "queue-stress: FAIL QueueStress $1 jebena=$G java=$QEXP"; fail=1; }; done; }
qcheck "JEBENA_CARRIERS=1" 3
qcheck "JEBENA_CARRIERS=4" 10
qcheck "JEBENA_GC_INTERVAL=400 JEBENA_CARRIERS=4" 5
[ "$fail" = 0 ] || exit 1
echo "queue-stress: OK — ArrayDeque growth/wrap + LinkedBlockingQueue producer/consumer (park) = $QEXP (carriers 1 & 4, +GC), matches real java"
