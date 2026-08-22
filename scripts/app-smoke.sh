#!/usr/bin/env bash
# Bigger end-to-end real program: a word-frequency counter exercising String.split
# (regex), HashMap get/put/entrySet/values, ArrayList, Collections.sort with a lambda
# Comparator, Collection.stream() -> filter/map/sorted/findFirst + mapToInt/sum,
# recursion, and a caught NumberFormatException — all in one program. Its full stdout
# must be byte-identical to real java. Finds integration bugs a single-feature
# differential case can't. jbase is passed EAGERLY.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-app
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/WordFreq.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

fail=0
check_run() { # label; args...
  local label="$1"; shift
  local want got
  want="$("$JAVA" -cp "$OUT" WordFreq "$@")"
  got="$(timeout 60 "$JEBENA" run WordFreq main "$OUT" "${JBASE[@]}" -- "$@" 2>/dev/null)"
  if [ "$want" = "$got" ]; then
    echo "  OK   $label"
  else
    echo "  FAIL $label"; echo "--- java ---"; printf '%s\n' "$want"; echo "--- jebena ---"; printf '%s\n' "$got"; fail=1
  fi
}

check_run "builtin text"
check_run "argv text" foo bar foo baz bar foo

if [ "$fail" -eq 0 ]; then echo "app-smoke: OK (WordFreq end-to-end byte-identical to real java)"; exit 0; else echo "app-smoke: FAIL"; exit 1; fi
