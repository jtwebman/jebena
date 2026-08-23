#!/usr/bin/env bash
# Dijkstra single-source shortest paths on a weighted digraph (TreeMap adjacency of
# ArrayList<int[]{to,weight}>, distance array, linear min-scan). Exercises TreeMap,
# ArrayList holding int[] elements, arrays, boxing, and a classic algorithm end-to-end.
# stdout byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-dij
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/Dijkstra.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" Dijkstra)"
got="$(timeout 60 "$JEBENA" run Dijkstra main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then
  echo "dijkstra-smoke: OK (shortest paths byte-identical to real java)"; exit 0
else
  echo "dijkstra-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got") | head; exit 1
fi
