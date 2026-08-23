#!/usr/bin/env bash
# Event-schedule report end-to-end: java.time (LocalDate/DayOfWeek/Month via toEpochDay),
# java.text.DecimalFormat money formatting ("#,##0.00"), and java.nio.file.Paths/Path
# building + normalizing the report path. Exercises the newest clean-room classes together
# in one real program. stdout must be byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-event
rm -rf "$OUT"; mkdir -p "$OUT"
[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"
"$JAVAC" -d "$OUT" "$ROOT/test/app/EventReport.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')
want="$("$JAVA" -cp "$OUT" EventReport)"
got="$(timeout 60 "$JEBENA" run EventReport main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"
if [ "$want" = "$got" ]; then echo "event-smoke: OK (LocalDate/DayOfWeek/Month + DecimalFormat + nio Path report byte-identical to real java)"; exit 0
else echo "event-smoke: MISMATCH"; diff <(printf '%s\n' "$want") <(printf '%s\n' "$got")|head -20; exit 1; fi
