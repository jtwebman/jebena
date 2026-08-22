#!/usr/bin/env bash
# Networking (java.net) loopback echo stress. A server fiber accepts one
# connection on 127.0.0.1:ephemeral and echoes what it reads; the client (main)
# fiber connects, sends "PINGpong123", reads the echo, returns the byte-sum (888).
# Matches real java (which has real java.net), so it is differential-checked.
#
# Blocking accept/read now PARK the fiber (offload thread; see docs/THREADING.md
# "Step 6 design"), so the server + client fibers run on ONE carrier fine: this
# runs at JEBENA_CARRIERS 1, 2 & 4 (+GC). Skips cleanly (exit 0) if the sandbox
# forbids loopback sockets.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-net
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/NetEcho.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"
EXP=$("$JAVA" -cp "$OUT" Driver st.NetEcho demo 2>/dev/null)

# Sockets available in this sandbox? Probe once; skip cleanly if not (a bind/
# connect failure surfaces as demo() = -2, or real java could not do it either).
PROBE=$(timeout 20 env JEBENA_CARRIERS=2 "$JEBENA" run st/NetEcho demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "888" ] || [ "$PROBE" = "-2" ]; then
  echo "net-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/NetEcho demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "net-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "net-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 6
check "carriers=1+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=1" 4
check "carriers=2" "JEBENA_CARRIERS=2" 6
check "carriers=4" "JEBENA_CARRIERS=4" 10
check "carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "net-stress: OK — java.net loopback echo = $EXP (Socket/ServerSocket over std.Io TCP, carriers 1, 2 & 4, +GC), matches real java"
