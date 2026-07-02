#!/usr/bin/env bash
set -euo pipefail

CHAIN_NAME="${1:-qingdao}"

case "$CHAIN_NAME" in
  qingdao)
    CONSOLE_DIR="/home/ubuntu/blockchain/fisco-console-qingdao-test/console"
    ;;
  weifang)
    CONSOLE_DIR="/home/ubuntu/blockchain/fisco-console-weifang-test/console"
    ;;
  relay)
    CONSOLE_DIR="/home/ubuntu/blockchain/fisco-console-relay-test/console"
    ;;
  *)
    echo "unsupported chain: $CHAIN_NAME" >&2
    echo "usage: ./open_fisco_console.sh [qingdao|weifang|relay]" >&2
    exit 1
    ;;
esac

if [[ ! -d "$CONSOLE_DIR" ]]; then
  echo "console dir not found for chain '$CHAIN_NAME': $CONSOLE_DIR" >&2
  echo "tip: current server may only have qingdao console prepared." >&2
  exit 1
fi

cd "$CONSOLE_DIR"

echo "Opening official FISCO BCOS console for chain: $CHAIN_NAME"
echo "Console dir: $CONSOLE_DIR"
echo "Tip: inside console, try commands like:"
echo "  getBlockNumber"
echo "  listAbi SgccTrustAnchor"
echo "  getCode 0xafcca7e2ca495ca06a4715ef4ce6457958c42a6e"
echo "  help"
echo

exec bash start.sh
