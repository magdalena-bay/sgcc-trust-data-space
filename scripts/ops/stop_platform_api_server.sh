#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"

stop_unit() {
  local unit_name="$1"
  sudo systemctl stop "${unit_name}.service" 2>/dev/null || true
  sudo systemctl reset-failed "${unit_name}.service" 2>/dev/null || true
}

case "${MODE}" in
  live)
    stop_unit "sgcc-platform-api"
    ;;
  8node)
    stop_unit "sgcc-platform-api-8node"
    ;;
  all)
    stop_unit "sgcc-platform-api"
    stop_unit "sgcc-platform-api-8node"
    ;;
  *)
    echo "unsupported mode: ${MODE}. use live, 8node, or all" >&2
    exit 1
    ;;
esac

echo "backend units stopped: ${MODE}"
