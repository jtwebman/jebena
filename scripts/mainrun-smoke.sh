#!/usr/bin/env bash
# Real-program entry point: `jebena run <Main> main <classpath>... -- <args>`
# invokes public static void main(String[]) with argv marshalled into a real
# String[]. Assert the full stdout of a small program is byte-identical to real
# java. This is the "run an actual program" milestone, not a reflection-invoked
# no-arg method. jbase is passed EAGERLY (real clean-room bytecode).
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-mainrun
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

cat > "$OUT/HelloArgs.java" <<'JAVA'
public class HelloArgs {
    public static void main(String[] args) {
        System.out.println("Hello, jebena!");
        System.out.println("argc=" + args.length);
        int sum = 0;
        for (int i = 0; i < args.length; i++) {
            System.out.println("arg[" + i + "]=" + args[i]);
            sum += Integer.parseInt(args[i]);
        }
        System.out.println("sum=" + sum);
    }
}
JAVA
"$JAVAC" -d "$OUT" "$OUT/HelloArgs.java" || { echo "javac failed"; exit 1; }

ARGS=(10 20 12)
want="$("$JAVA" -cp "$OUT" HelloArgs "${ARGS[@]}")"

mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
# jebena prints program output to stdout; the trailing "returned void" diagnostic
# goes to stderr, so plain stdout capture is the program's own output.
got="$(timeout 60 "$JEBENA" run HelloArgs main "$OUT" "${JBASE[@]}" -- "${ARGS[@]}" 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "mainrun-smoke: OK (main(String[]) output byte-identical to java)"
  exit 0
else
  echo "mainrun-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
