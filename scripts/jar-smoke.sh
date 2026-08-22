#!/usr/bin/env bash
# `jebena -jar <app.jar> <jbase-dir> -- <args>` reads Main-Class from the jar's
# META-INF/MANIFEST.MF and runs it, loading the app's classes lazily FROM the jar.
# A multi-class program (App + Greeter, ArrayList, constructor/fields, enhanced-for,
# string concat) must produce stdout byte-identical to real `java -jar`. jbase is a
# directory here (lazy) — the app doesn't use Arrays/java.time, so no intrinsic
# shadowing; programs needing eager jbase can pass explicit .class files to `run`.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
JAR="$(dirname "$JAVAC")/jar"
[ -x "$JAR" ] || JAR=jar
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-jar
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

cat > "$OUT/Greeter.java" <<'JAVA'
public class Greeter {
    private final String who;
    Greeter(String who) { this.who = who; }
    String greet() { return "Hi, " + who + "!"; }
}
JAVA
cat > "$OUT/App.java" <<'JAVA'
import java.util.ArrayList;
public class App {
    public static void main(String[] args) {
        ArrayList<String> names = new ArrayList<>();
        for (String a : args) names.add(a);
        if (names.isEmpty()) names.add("world");
        for (String n : names) {
            System.out.println(new Greeter(n).greet());
        }
        System.out.println("count=" + names.size());
    }
}
JAVA
"$JAVAC" -d "$OUT" "$OUT/App.java" "$OUT/Greeter.java" || { echo "javac failed"; exit 1; }
( cd "$OUT" && "$JAR" cfe app.jar App App.class Greeter.class ) || { echo "jar failed"; exit 1; }

ARGS=(Ann Bob)
want="$("$JAVA" -jar "$OUT/app.jar" "${ARGS[@]}")"
got="$(timeout 60 "$JEBENA" -jar "$OUT/app.jar" "$ROOT/jbase/out" -- "${ARGS[@]}" 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "jar-smoke: OK (java -jar Main-Class from manifest, multi-class app, stdout parity)"
  exit 0
else
  echo "jar-smoke: MISMATCH"
  echo "--- java ---"; printf '%s\n' "$want"
  echo "--- jebena ---"; printf '%s\n' "$got"
  exit 1
fi
