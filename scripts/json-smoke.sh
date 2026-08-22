#!/usr/bin/env bash
# A JSON-ish pretty-printer over a nested LinkedHashMap/ArrayList model. Exercises
# recursion, instanceof type dispatch, ordered-map iteration, StringBuilder, boxing,
# null, and empty collections — full stdout byte-identical to real java. jbase EAGER.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-json
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/JsonPrint.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" JsonPrint)"
got="$(timeout 60 "$JEBENA" run JsonPrint main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "json-smoke: OK (nested Map/List pretty-print byte-identical to real java)"
  exit 0
else
  echo "json-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
