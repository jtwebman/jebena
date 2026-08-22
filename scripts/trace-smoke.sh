#!/usr/bin/env bash
# Full uncaught-exception stack trace: `Exception in thread "main" <type>: <msg>`
# followed by `\tat Class.method(File:line)` frames, byte-identical to real java.
# Exercises Throwable.fillInStackTrace (native frame capture), StackTraceElement,
# LineNumberTable/SourceFile mapping, and the multi-frame call chain. jbase EAGER.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-trace
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

cat > "$OUT/Boom.java" <<'JAVA'
public class Boom {
    static int level3() {
        throw new RuntimeException("kaboom");
    }
    static int level2() {
        return level3();
    }
    static int level1() {
        return level2();
    }
    public static void main(String[] args) {
        System.out.println("start");
        level1();
    }
}
JAVA
"$JAVAC" -d "$OUT" "$OUT/Boom.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" Boom 2>&1 1>/dev/null)"
got="$(timeout 60 "$JEBENA" run Boom main "$OUT" "${JBASE[@]}" -- 2>&1 1>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "trace-smoke: OK (full stack trace byte-identical to real java)"
  exit 0
else
  echo "trace-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
