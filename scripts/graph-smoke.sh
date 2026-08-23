#!/usr/bin/env bash
# Directed-graph BFS + Kahn topological sort over a TreeMap adjacency (sorted,
# deterministic) with ArrayDeque queues and a TreeSet visited set; reports a cycle
# when a topo order can't be produced. Exercises TreeMap/ArrayDeque/TreeSet, boxing,
# and multi-collection composition end-to-end. stdout byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-graph
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/Graph.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" Graph)"
got="$(timeout 60 "$JEBENA" run Graph main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "graph-smoke: OK (BFS + topological sort + cycle detection byte-identical to real java)"
  exit 0
else
  echo "graph-smoke: MISMATCH"
  diff <(printf '%s\n' "$want") <(printf '%s\n' "$got") | head -20
  exit 1
fi
