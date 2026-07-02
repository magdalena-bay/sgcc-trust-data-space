#!/usr/bin/env bash
set -euo pipefail

CONSOLE_DIR="${CONSOLE_DIR:-/home/ubuntu/blockchain/fisco-console-qingdao-test/console}"

if [[ ! -d "$CONSOLE_DIR" ]]; then
  echo "console dir not found: $CONSOLE_DIR" >&2
  exit 1
fi

cd "$CONSOLE_DIR"

set +e
OUTPUT="$(./console.sh "$@" 2>&1)"
STATUS=$?
set -e

printf '%s\n' "$OUTPUT" | sed '/libproviders\.so: cannot open shared object file/d'
exit "$STATUS"
