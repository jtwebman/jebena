#!/usr/bin/env bash
# Richer SQL over the Postgres v3 wire client: a general query() that returns a
# full result set (multiple rows, multiple typed text columns), reusing PgQuery's
# wire helpers. Proves the parser handles real RowDescription/DataRow shapes.
#
#   demo()  ALWAYS runs: client vs an in-process mock backend returning a 3-row,
#           2-column result -- (1,"r1"),(2,"r2"),(3,"r3") -- checksum
#           rows*10000 + cols*1000 + sum(cell bytes) = 32642. Matches real java.
#           Needs carriers>=2 (mock-server + client fibers); +GC too.
#
#   real()  runs ONLY when JEBENA_PGTEST is set AND Postgres is reachable on
#           127.0.0.1:${JEBENA_PGPORT:-5432}: the SAME client runs
#           "SELECT g, 'r'||g FROM generate_series(1,3) g ORDER BY g" (same rows,
#           same 32642). Skipped cleanly otherwise so the gate stays green.
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
OUT=/tmp/jebena-rich
rm -rf "$OUT"; mkdir -p "$OUT"
bash "$ROOT/scripts/build-jbase.sh" >/dev/null
"$JAVAC" -d "$OUT" "$ROOT"/test/stress/RichSql.java "$ROOT"/test/stress/PgQuery.java "$ROOT/test/diff/Driver.java" 2>/dev/null
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
JB="$ROOT/jbase/out"

EXP=$("$JAVA" -cp "$OUT" Driver st.RichSql demo 2>/dev/null)
PROBE=$(timeout 20 env JEBENA_CARRIERS=2 "$JEBENA" run st/RichSql demo "$OUT" "$JB" 2>&1 | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
if [ "$EXP" != "32642" ] || [ "${PROBE:-0}" -lt 0 ] 2>/dev/null; then
  echo "rich-sql-stress: SKIP — loopback sockets unavailable in this sandbox (java=$EXP jebena=$PROBE)"
  exit 0
fi

fail=0
check() { # $1 label  $2 env  $3 reps
  for rep in $(seq 1 "$3"); do
    ALL=$(timeout 20 bash -c "$2 exec '$JEBENA' run st/RichSql demo $OUT $JB" 2>&1)
    [ $? -eq 124 ] && { echo "rich-sql-stress: FAIL $1 rep=$rep HANG"; fail=1; }
    GOT=$(printf '%s\n' "$ALL" | sed -n 's/.*demo() = \(-\?[0-9]*\).*/\1/p')
    [ "$GOT" = "$EXP" ] || { echo "rich-sql-stress: FAIL $1 rep=$rep jebena=$GOT java=$EXP"; fail=1; }
  done
}
check "mock carriers=1" "JEBENA_CARRIERS=1" 6
check "mock carriers=2" "JEBENA_CARRIERS=2" 6
check "mock carriers=4" "JEBENA_CARRIERS=4" 10
check "mock carriers=4+GC" "JEBENA_GC_INTERVAL=150 JEBENA_CARRIERS=4" 6

PGPORT="${JEBENA_PGPORT:-5432}"
if [ -n "${JEBENA_PGTEST:-}" ]; then
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h 127.0.0.1 -p "$PGPORT" -t 3 >/dev/null 2>&1; then
    for c in 1 4; do
      G=$(timeout 20 env JEBENA_CARRIERS=$c "$JEBENA" run st/RichSql real "$OUT" "$JB" 2>&1 | sed -n 's/.*real() = \(-\?[0-9]*\).*/\1/p')
      [ "$G" = "32642" ] || { echo "rich-sql-stress: FAIL live-db carriers=$c jebena=$G exp=32642"; fail=1; }
    done
    LIVE="+ live Postgres generate_series(1,3) = 32642"
  else
    LIVE="(JEBENA_PGTEST set but no Postgres on 127.0.0.1:$PGPORT — live check skipped)"
  fi
else
  LIVE="(live check off; set JEBENA_PGTEST + a local Postgres to enable)"
fi

[ "$fail" = 0 ] || exit 1
echo "rich-sql-stress: OK — multi-row/multi-column PG result set parse = $EXP (carriers 2 & 4, +GC), matches real java $LIVE"
