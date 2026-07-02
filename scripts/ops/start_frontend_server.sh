#!/usr/bin/env bash
set -euo pipefail

MODE="dev"
REPO_DIR="/home/ubuntu/sgcc-trust-data-space"
HOST="127.0.0.1"
PORT=""
MEMORY_MAX=""
NODE_MAX_OLD_SPACE_MB=""
UNIT_NAME=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift 2
      ;;
    --repo-dir)
      REPO_DIR="$2"
      shift 2
      ;;
    --host)
      HOST="$2"
      shift 2
      ;;
    --port)
      PORT="$2"
      shift 2
      ;;
    --memory-max)
      MEMORY_MAX="$2"
      shift 2
      ;;
    --node-max-old-space-mb)
      NODE_MAX_OLD_SPACE_MB="$2"
      shift 2
      ;;
    *)
      echo "unsupported argument: $1" >&2
      exit 1
      ;;
  esac
done

case "$MODE" in
  dev)
    PORT="${PORT:-5173}"
    MEMORY_MAX="${MEMORY_MAX:-768M}"
    NODE_MAX_OLD_SPACE_MB="${NODE_MAX_OLD_SPACE_MB:-512}"
    UNIT_NAME="sgcc-user-web-dev"
    START_COMMAND="exec npm run dev -- --host ${HOST} --port ${PORT} --strictPort"
    ;;
  preview)
    PORT="${PORT:-4173}"
    MEMORY_MAX="${MEMORY_MAX:-1024M}"
    NODE_MAX_OLD_SPACE_MB="${NODE_MAX_OLD_SPACE_MB:-768}"
    UNIT_NAME="sgcc-user-web-preview"
    START_COMMAND="npm run build && exec npm run preview -- --host ${HOST} --port ${PORT} --strictPort"
    ;;
  *)
    echo "unsupported mode: ${MODE}. use dev or preview" >&2
    exit 1
    ;;
esac

APP_DIR="${REPO_DIR}/frontend/user-web"
LOG_FILE="/tmp/${UNIT_NAME}.log"

if [[ ! -f "${APP_DIR}/package.json" ]]; then
  echo "frontend directory not found: ${APP_DIR}" >&2
  exit 1
fi

if [[ ! -d "${APP_DIR}/node_modules" ]]; then
  echo "missing ${APP_DIR}/node_modules, run npm install first" >&2
  exit 1
fi

sudo systemctl stop "${UNIT_NAME}.service" 2>/dev/null || true
sudo systemctl reset-failed "${UNIT_NAME}.service" 2>/dev/null || true
rm -f "${LOG_FILE}"

sudo systemd-run \
  --unit="${UNIT_NAME}" \
  --uid=ubuntu \
  --gid=ubuntu \
  --working-directory="${APP_DIR}" \
  --property=Type=simple \
  --property=Restart=on-failure \
  --property=RestartSec=5 \
  --property=TimeoutStopSec=15 \
  --property=MemoryMax="${MEMORY_MAX}" \
  --property=TasksMax=256 \
  --property=LimitNOFILE=65536 \
  --property=OOMPolicy=stop \
  --setenv=NODE_ENV=development \
  --setenv=BROWSER=none \
  --setenv=CHOKIDAR_USEPOLLING=false \
  --setenv=NODE_OPTIONS=--max-old-space-size="${NODE_MAX_OLD_SPACE_MB}" \
  --setenv=VITE_API_BASE=/api \
  /bin/bash -lc "${START_COMMAND} >>'${LOG_FILE}' 2>&1"

for _ in $(seq 1 30); do
  if curl -fsS "http://${HOST}:${PORT}/" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -fsS "http://${HOST}:${PORT}/" >/dev/null 2>&1; then
  echo "frontend ${MODE} server did not become ready" >&2
  sudo systemctl status "${UNIT_NAME}.service" --no-pager -l >&2 || true
  tail -n 200 "${LOG_FILE}" >&2 || true
  exit 1
fi

echo "started ${UNIT_NAME} on http://${HOST}:${PORT}"
echo "log: ${LOG_FILE}"
echo "tip: pair this with nginx proxying / -> ${HOST}:${PORT} and /api -> 127.0.0.1:8088"
