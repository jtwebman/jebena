#!/usr/bin/env bash
# A bank-ledger state machine with a USER-DEFINED exception subclass
# (OverdraftException extends RuntimeException): apply deposit/withdraw ops to a
# TreeMap of accounts, throw+catch overdraft per-op, print sorted balances + total.
# Exercises custom-throwable construction/throw/catch-by-declared-type, sorted map
# iteration, boxing, and string concat end-to-end. stdout byte-identical to real java.
set -u
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
JDK_HOME="${JEBENA_JDK:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA="$JDK_HOME/bin/java"
[ -x "$JAVA" ] || JAVA=java
JAVAC="$(command -v javac || echo /usr/lib/jvm/java-17-openjdk-amd64/bin/javac)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT=/tmp/jebena-bank
rm -rf "$OUT"; mkdir -p "$OUT"

[ -d "$ROOT/jbase/out" ] || bash "$ROOT/scripts/build-jbase.sh" >/dev/null 2>&1
"$ZIG" build --build-file "$ROOT/build.zig" >/dev/null 2>&1
JEBENA="$ROOT/zig-out/bin/jebena"

"$JAVAC" -d "$OUT" "$ROOT/test/app/BankLedger.java" || { echo "javac failed"; exit 1; }
mapfile -t JBASE < <(find "$ROOT/jbase/out" -name '*.class')

want="$("$JAVA" -cp "$OUT" BankLedger)"
got="$(timeout 60 "$JEBENA" run BankLedger main "$OUT" "${JBASE[@]}" -- 2>/dev/null)"

if [ "$want" = "$got" ]; then
  echo "bank-smoke: OK (bank-ledger + custom OverdraftException byte-identical to real java)"
  exit 0
else
  echo "bank-smoke: MISMATCH"
  diff <(printf '%s\n' "$want") <(printf '%s\n' "$got") | head -20
  exit 1
fi
