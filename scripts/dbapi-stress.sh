#!/usr/bin/env bash
# The capstone: a Postgres-backed HTTP API on jebena. An HTTP server fiber answers
# a GET by opening a Postgres connection (PgQuery's v3 wire client), running
# SELECT 1, and returning the value in the HTTP body ("db=1"); the client (main)
# fiber GETs "/" and returns the body byte-sum (308). This exercises java.net
# sockets + hand-written HTTP + the PG wire protocol end to end.
#
#   demo()  ALWAYS runs: HTTP server backed by an in-process spec-faithful MOCK PG
#           backend, so the whole chain is green with no external DB. Matches real
#           java. Three fibers block concurrently (HTTP client, HTTP server, PG
#           backend) and blocking I/O holds a carrier (no fiber parking yet), so
#           this runs at carriers 3 & 4 (+GC).
#
#   real()  runs ONLY when JEBENA_PGTEST is set AND a Postgres is reachable on
#           127.0.0.1:${JEBENA_PGPORT:-5432}: the HTTP server queries a LIVE DB.
#           Skipped cleanly otherwise so the gate stays green.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-dbapi
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/DbApi.java "$ROOT"/test/stress/PgQuery.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"

EXP=$("$JAVA" -cp "$OUT" Driver st.DbApi demo 2>/dev/null)
PROBE=$(timeout 20 env JEBENA_CARRIERS=4 "$JEBENA" run st/DbApi demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "308" ] || [ "${PROBE:-0}" -lt 0 ] 2>/dev/null; then
  echo "dbapi-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/DbApi demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "dbapi-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "dbapi-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "mock carriers=1" "JEBENA_CARRIERS=1" 5
check "mock carriers=3" "JEBENA_CARRIERS=3" 5
check "mock carriers=4" "JEBENA_CARRIERS=4" 10
check "mock carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6

# --- optional live-DB end-to-end (opt-in) ---
PGPORT="${JEBENA_PGPORT:-5432}"
if [ -n "${JEBENA_PGTEST:-}" ]; then
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h 127.0.0.1 -p "$PGPORT" -t 3 >/dev/null 2>&1; then
    for c in 3 4; do
      G=$(timeout 20 env JEBENA_CARRIERS=$c "$JEBENA" run st/DbApi real "$OUT" "$JB" 2>&1 | sed -n 's/.*real() = \(-\?[0-9]*\).*/\1/p')
      [ "$G" = "308" ] || { echo "dbapi-stress: FAIL live-db carriers=$c jebena=$G exp=308"; fail=1; }
    done
    LIVE="+ live Postgres-backed HTTP GET = db=1"
  else
    LIVE="(JEBENA_PGTEST set but no Postgres on 127.0.0.1:$PGPORT — live check skipped)"
  fi
else
  LIVE="(live check off; set JEBENA_PGTEST + a local Postgres to enable)"
fi

[ "$fail" = 0 ] || exit 1
echo "dbapi-stress: OK — Postgres-backed HTTP API (GET -> PG SELECT 1 -> HTTP body db=1) = $EXP (carriers 3 & 4, +GC), matches real java $LIVE"
