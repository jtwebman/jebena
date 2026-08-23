#!/usr/bin/env bash
# LinkedBlockingQueue producer/consumer with a DISTINCT-value checksum: 6 producers
# each put 200 unique values (p*10000+j), 3 consumers take() (parking on empty) and
# accumulate into an AtomicLong until a poison pill. Because every value is unique, a
# lost/duplicated/reordered item OR a lost wakeup changes the sum (or hangs). Expected
# total = 30,120,600. Must match real java at carriers 1 & 4 and with GC forced (the
# moving collector remaps the queue nodes + boxed elements while fibers park).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-lbq
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/LbqStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.LbqStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 150 env $1 "$JEBENA" run st/LbqStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "lbq-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "lbq-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 2
check "JEBENA_CARRIERS=4" 4
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 3
[ "$fail" = 0 ] || exit 1
echo "lbq-stress: OK — LinkedBlockingQueue producer/consumer distinct-value checksum across carriers (1 & 4, +GC) = $EXP, matches real java"
