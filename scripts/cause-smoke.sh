#!/usr/bin/env bash
# Throwable cause chaining: `new RuntimeException(msg, cause)` (a user-thrown cause,
# so all frames are user code) prints a `Caused by:` section with common-frame
# elision (`\t... N more`) byte-identical to real java. Exercises the (String,
# Throwable) constructor, getCause, and reportUncaught's cause-chain walk. jbase EAGER.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-cause
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

cat > "$OUT/Cause.java" <<'JAVA'
public class Cause {
    static void inner() {
        throw new IllegalStateException("inner boom");
    }
    static void middle() {
        try {
            inner();
        } catch (IllegalStateException e) {
            throw new RuntimeException("middle failed", e);
        }
    }
    public static void main(String[] args) {
        System.out.println("go");
        middle();
    }
}
JAVA
"$JAVAC" -d "$OUT" "$OUT/Cause.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" Cause 2>&1 1>/dev/null)"
got="$(timeout 60 "$JEBENA" run Cause main "$OUT" "${JBASE[@]}" -- 2>&1 1>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "cause-smoke: OK (Caused-by chain + '... N more' elision byte-identical to real java)"
  exit 0
else
  echo "cause-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
