#!/usr/bin/env bash
# Pull local reference copies of the Java SE 25 specs. Gitignored; not redistributed.
set -euo pipefail
cd "$(dirname "$0")"
curl -fL -o jls25.pdf  https://docs.oracle.com/javase/specs/jls/se25/jls25.pdf
curl -fL -o jvms25.pdf https://docs.oracle.com/javase/specs/jvms/se25/jvms25.pdf
echo "Fetched jls25.pdf and jvms25.pdf"
