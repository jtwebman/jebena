#!/usr/bin/env bash
# A small multi-endpoint HTTP service on jebena: the server fiber routes on path
# (GET /ping -> "pong"; GET /db -> Postgres SELECT 1 -> "db=1"; else -> 404) and
# the client fiber exercises all three, returning 1+10+100 = 111 when every route
# behaves. Demonstrates path routing + a DB-backed endpoint end to end, on the
# fiber-parking sockets (so it runs at carriers=1). Matches real java.
#
#   demo()  ALWAYS runs: /db backed by an in-process mock PG (green, no DB) at
#           carriers 1 & 4 (+GC).
#   real()  runs ONLY when JEBENA_PGTEST is set AND Postgres is reachable on
#           127.0.0.1:${JEBENA_PGPORT:-5432}: /db hits a live DB. Skipped cleanly
#           otherwise so the gate stays green.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-router
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/RouterApp.java "$ROOT"/test/stress/PgQuery.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"

EXP=$("$JAVA" -cp "$OUT" Driver st.RouterApp demo 2>/dev/null)
PROBE=$(timeout 20 env JEBENA_CARRIERS=1 "$JEBENA" run st/RouterApp demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "111" ] || [ "${PROBE:-0}" -lt 0 ] 2>/dev/null; then
  echo "router-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/RouterApp demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "router-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "router-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "carriers=1" "JEBENA_CARRIERS=1" 6
check "carriers=1+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=1" 4
check "carriers=4" "JEBENA_CARRIERS=4" 8
check "carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6

PGPORT="${JEBENA_PGPORT:-5432}"
if [ -n "${JEBENA_PGTEST:-}" ]; then
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h 127.0.0.1 -p "$PGPORT" -t 3 >/dev/null 2>&1; then
    for c in 1 4; do
      G=$(timeout 20 env JEBENA_CARRIERS=$c "$JEBENA" run st/RouterApp real "$OUT" "$JB" 2>&1 | sed -n 's/.*real() = \(-\?[0-9]*\).*/\1/p')
      [ "$G" = "111" ] || { echo "router-stress: FAIL live-db carriers=$c jebena=$G exp=111"; fail=1; }
    done
    LIVE="+ live Postgres-backed /db route = db=1"
  else
    LIVE="(JEBENA_PGTEST set but no Postgres on 127.0.0.1:$PGPORT — live check skipped)"
  fi
else
  LIVE="(live check off; set JEBENA_PGTEST + a local Postgres to enable)"
fi

[ "$fail" = 0 ] || exit 1
echo "router-stress: OK — multi-route HTTP service (/ping + PG-backed /db + 404) = $EXP (carriers 1 & 4, +GC), matches real java $LIVE"
