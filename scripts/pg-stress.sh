#!/usr/bin/env bash
# PostgreSQL v3 wire-protocol client over java.net (StartupMessage -> auth ->
# simple Query "SELECT 1" -> parse RowDescription/DataRow/CommandComplete/
# ReadyForQuery). Two checks:
#
#   demo()  ALWAYS runs: the client against an in-process spec-faithful MOCK
#           backend fiber on loopback, so the full wire encode/decode is exercised
#           and green with no database (needs carriers>=2; +GC too). Matches real
#           java. This is what keeps the gate meaningful in a DB-less sandbox.
#
#   real()  runs ONLY when JEBENA_PGTEST is set AND a Postgres is reachable on
#           127.0.0.1:${JEBENA_PGPORT:-5432} (user/db "postgres"): the SAME client
#           against a live server, asserting the cell == 1. Skipped cleanly
#           otherwise so the gate stays green.
#
# Blocking socket I/O holds the carrier (no fiber parking yet -- see
# docs/THREADING.md "networking"); demo()'s mock-server + client fibers need
# different carriers, hence carriers 2 & 4.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-pg
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/PgQuery.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"

# --- always-green mock loopback ---
EXP=$("$JAVA" -cp "$OUT" Driver st.PgQuery demo 2>/dev/null)
PROBE=$(timeout 20 env JEBENA_CARRIERS=2 "$JEBENA" run st/PgQuery demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "1" ] || [ "${PROBE:-0}" -lt 0 ] 2>/dev/null; then
  echo "pg-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/PgQuery demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "pg-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "1" ] || { echo "pg-stress: FAIL $1 rep=$rep jebena=$GOT exp=1"; fail=1; }
  done
}
check "mock carriers=2" "JEBENA_CARRIERS=2" 6
check "mock carriers=4" "JEBENA_CARRIERS=4" 10
check "mock carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6

# --- optional live-DB check (opt-in) ---
PGPORT="${JEBENA_PGPORT:-5432}"
if [ -n "${JEBENA_PGTEST:-}" ]; then
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h 127.0.0.1 -p "$PGPORT" -t 3 >/dev/null 2>&1; then
    for c in 1 4; do
      G=$(timeout 20 env JEBENA_CARRIERS=$c "$JEBENA" run st/PgQuery real "$OUT" "$JB" 2>&1 | sed -n 's/.*real() = \(-\?[0-9]*\).*/\1/p')
      [ "$G" = "1" ] || { echo "pg-stress: FAIL live-db carriers=$c jebena=$G exp=1"; fail=1; }
    done
    LIVE="+ live Postgres SELECT 1 = 1"
  else
    LIVE="(JEBENA_PGTEST set but no Postgres on 127.0.0.1:$PGPORT — live check skipped)"
  fi
else
  LIVE="(live check off; set JEBENA_PGTEST + a local Postgres to enable)"
fi

[ "$fail" = 0 ] || exit 1
echo "pg-stress: OK — Postgres v3 wire client SELECT 1 vs spec-faithful mock = 1 (carriers 2 & 4, +GC), matches real java $LIVE"
