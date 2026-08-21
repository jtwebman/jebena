#!/usr/bin/env bash
# Proves the VM stays portable: cross-compile the whole Jebena binary (interpreter,
# GC, class loader, java.base natives) for non-host OSes/arches. The OS-boundary
# natives (System.out write, clocks) go through Zig's portable std.Io, so no target
# should need per-OS VM code. (Windows CLI arg parsing in main.zig is a known TODO.)
set -eu
ZIG=~/.local/zig-x86_64-linux-0.16.0/zig
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGETS="aarch64-macos x86_64-macos aarch64-linux x86_64-linux"
fail=0
for tgt in $TARGETS; do
  if "$ZIG" build --build-file "$ROOT/build.zig" -Dtarget="$tgt" >/tmp/port-$tgt.txt 2>&1; then
    echo "  ok   $tgt"
  else
    echo "  FAIL $tgt"; grep -E "error:" /tmp/port-$tgt.txt | head -3; fail=1
  fi
done
[ "$fail" -eq 0 ] && echo "portability-check: OK — VM cross-compiles for all targets" || { echo "portability-check: FAILED"; exit 1; }
