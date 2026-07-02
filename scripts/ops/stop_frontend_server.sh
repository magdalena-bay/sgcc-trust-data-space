#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"

stop_unit() {
  local unit_name="$1"
  sudo systemctl stop "${unit_name}.service" 2>/dev/null || true
  sudo systemctl reset-failed "${unit_name}.service" 2>/dev/null || true
}

case "${MODE}" in
  dev)
    stop_unit "sgcc-user-web-dev"
    ;;
  preview)
    stop_unit "sgcc-user-web-preview"
    ;;
  all)
    stop_unit "sgcc-user-web-dev"
    stop_unit "sgcc-user-web-preview"
    ;;
  *)
    echo "unsupported mode: ${MODE}. use dev, preview, or all" >&2
    exit 1
    ;;
esac

echo "frontend units stopped: ${MODE}"
