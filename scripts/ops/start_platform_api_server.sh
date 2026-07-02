#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/home/ubuntu/sgcc-trust-data-space/.server.env"
REPO_DIR="/home/ubuntu/sgcc-trust-data-space"
MEMORY_MAX="${MEMORY_MAX:-1536M}"
JAVA_XMS="${JAVA_XMS:-256m}"
JAVA_XMX="${JAVA_XMX:-1024m}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --repo-dir)
      REPO_DIR="$2"
      shift 2
      ;;
    --memory-max)
      MEMORY_MAX="$2"
      shift 2
      ;;
    --java-xms)
      JAVA_XMS="$2"
      shift 2
      ;;
    --java-xmx)
      JAVA_XMX="$2"
      shift 2
      ;;
    *)
      echo "unsupported argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "env file not found: ${ENV_FILE}" >&2
  exit 1
fi

APP_DIR="${REPO_DIR}/backend/platform-api"
JAR_FILE="${APP_DIR}/target/platform-api-0.1.0.jar"

if [[ ! -f "${JAR_FILE}" ]]; then
  echo "jar not found: ${JAR_FILE}. run mvn -DskipTests package first" >&2
  exit 1
fi

if [[ "${ENV_FILE}" == *".server.env.8node" ]]; then
  UNIT_NAME="sgcc-platform-api-8node"
  LOG_FILE="/tmp/sgcc-platform-8node.log"
else
  UNIT_NAME="sgcc-platform-api"
  LOG_FILE="/tmp/sgcc-platform-live.log"
fi

sudo systemctl stop "${UNIT_NAME}.service" 2>/dev/null || true
sudo systemctl reset-failed "${UNIT_NAME}.service" 2>/dev/null || true

sudo systemd-run \
  --unit="${UNIT_NAME}" \
  --uid=ubuntu \
  --gid=ubuntu \
  --working-directory="${APP_DIR}" \
  --property=Type=simple \
  --property=Restart=on-failure \
  --property=RestartSec=5 \
  --property=TimeoutStopSec=20 \
  --property=MemoryMax="${MEMORY_MAX}" \
  --property=TasksMax=512 \
  --property=OOMPolicy=stop \
  /bin/bash -lc "set -a; . '${ENV_FILE}'; set +a; exec java -Xms${JAVA_XMS} -Xmx${JAVA_XMX} -jar '${JAR_FILE}' >>'${LOG_FILE}' 2>&1"

for _ in $(seq 1 30); do
  PORT="$(bash -lc "set -a; . '${ENV_FILE}'; set +a; printf '%s' \"\${SGCC_PLATFORM_PORT:-}\"")"
  if [[ -n "${PORT}" ]] && curl -fsS "http://127.0.0.1:${PORT}/api/demo/health" >/dev/null 2>&1; then
    echo "started ${UNIT_NAME} on http://127.0.0.1:${PORT}"
    echo "log: ${LOG_FILE}"
    exit 0
  fi
  sleep 1
done

echo "backend service did not become ready: ${UNIT_NAME}" >&2
sudo systemctl status "${UNIT_NAME}.service" --no-pager -l >&2 || true
tail -n 200 "${LOG_FILE}" >&2 || true
exit 1
