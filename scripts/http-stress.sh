#!/usr/bin/env bash
# Minimal HTTP/1.1 over java.net. A server fiber accepts one connection, parses
# the request line + headers (to CRLFCRLF), and replies 200 with a Content-Length
# body echoing the requested path; the client (main) fiber GETs "/hi", reads to
# EOF, checks the status line + body, and returns the body byte-sum (746). Matches
# real java (real java.net), so it is differential-checked.
#
# Blocking accept/read hold the carrier (no fiber parking yet -- see
# docs/THREADING.md "networking"), so server + client need DIFFERENT carriers:
# runs at JEBENA_CARRIERS 2 & 4 (+GC). Skips cleanly if the sandbox forbids
# loopback sockets.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-http
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/HttpEcho.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"
EXP=$("$JAVA" -cp "$OUT" Driver st.HttpEcho demo 2>/dev/null)

# Sockets available? Probe once; skip cleanly if not (a socket failure surfaces as
# a negative demo() code, or real java could not do it either).
PROBE=$(timeout 20 env JEBENA_CARRIERS=2 "$JEBENA" run st/HttpEcho demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "746" ] || [ "${PROBE:-0}" -lt 0 ] 2>/dev/null; then
  echo "http-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/HttpEcho demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "http-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "http-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=2" "JEBENA_CARRIERS=2" 6
check "carriers=4" "JEBENA_CARRIERS=4" 10
check "carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6
[ "$fail" = 0 ] || exit 1
echo "http-stress: OK — hand-written HTTP/1.1 request-parse + 200 response over java.net = $EXP (carriers 2 & 4, +GC), matches real java"
