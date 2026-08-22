#!/usr/bin/env bash
# Program termination semantics vs real java: clean exit (status 0),
# System.exit(n) (status n), and an uncaught exception (java-style
# "Exception in thread \"main\" <type>: <msg>" first line on stderr, status 1).
# A full stack trace needs StackTraceElement (not built yet); the type+message
# first line is what we assert. jbase is passed EAGERLY.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-exit
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

cat > "$OUT/ExitCodes.java" <<'JAVA'
public class ExitCodes {
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "clean";
        System.out.println("mode=" + mode);
        if (mode.equals("exit7")) {
            System.exit(7);
        }
        if (mode.equals("throw")) {
            throw new RuntimeException("boom " + mode);
        }
        System.out.println("done");
    }
}
JAVA
"$JAVAC" -d "$OUT" "$OUT/ExitCodes.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

fail=0
check() { # label want got
  if [ "$2" = "$3" ]; then echo "  OK   $1"; else echo "  FAIL $1: want [$2] got [$3]"; fail=1; fi
}
je() { timeout 60 "$JEBENA" run ExitCodes main "$OUT" "${JBASE[@]}" -- "$@"; }

# --- clean: stdout parity + exit 0 ---
jout="$(je clean 2>/dev/null)"; jrc=$?
gout="$("$JAVA" -cp "$OUT" ExitCodes clean 2>/dev/null)"
check "clean stdout" "$gout" "$jout"
check "clean exit"   "0" "$jrc"

# --- System.exit(7): exit status 7 ---
je exit7 >/dev/null 2>&1; jrc=$?
"$JAVA" -cp "$OUT" ExitCodes exit7 >/dev/null 2>&1; grc=$?
check "exit7 status" "$grc" "$jrc"

# --- uncaught throw: status 1 + stderr first line parity ---
jerr="$(je throw 2>&1 >/dev/null | head -1)"; je throw >/dev/null 2>&1; jrc=$?
gerr="$("$JAVA" -cp "$OUT" ExitCodes throw 2>&1 >/dev/null | head -1)"
"$JAVA" -cp "$OUT" ExitCodes throw >/dev/null 2>&1; grc=$?
check "throw stderr line1" "$gerr" "$jerr"
check "throw exit"         "$grc" "$jrc"

if [ "$fail" -eq 0 ]; then echo "exit-smoke: OK"; exit 0; else echo "exit-smoke: FAIL"; exit 1; fi
