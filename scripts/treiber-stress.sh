#!/usr/bin/env bash
# Treiber lock-free stack over AtomicReference.compareAndSet: 8 fibers push 250
# distinct values each (CAS the head), then 8 fibers pop everything (CAS the head)
# summing into an AtomicLong. Exercises reference-CAS under contention AND under the
# moving GC (head/next reference ids relocate while fibers spin on CAS). A non-atomic
# CAS loses pushes/pops. Expected 7,253,000. Must match real java at carriers 1 & 4 (+GC).
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-tre
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT/test/stress/TreiberStress.java" "$ROOT/test/diff/Driver.java"
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
mapfile -t APP < <(ls "$OUT"/st/*.class)

EXP=$("$JAVA" -cp "$OUT" Driver st.TreiberStress demo 2>/dev/null)
fail=0
check() { # env reps
  for rep in $(seq 1 "$2"); do
    ALL=$(timeout 45 env $1 "$JEBENA" run st/TreiberStress demo "${APP[@]}" "${JBASE[@]}" 2>&1)
    [ $? -eq 124 ] && { echo "treiber-stress: FAIL ($1) rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "treiber-stress: FAIL ($1) rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "JEBENA_CARRIERS=1" 3
check "JEBENA_CARRIERS=4" 8
check "JEBENA_GC_INTERVAL=200 JEBENA_CARRIERS=4" 5
[ "$fail" = 0 ] || exit 1
echo "treiber-stress: OK — Treiber stack via AtomicReference CAS across carriers (1 & 4, +GC) = $EXP, matches real java"
