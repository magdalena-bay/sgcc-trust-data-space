param(
  [ValidateSet("direct", "buffered_sync", "async")]
  [string]$Mode = "buffered_sync",
  [int]$WorkerBatchSize = 128,
  [int]$AnchorAggregateSize = 64,
  [int]$SubmitParallelism = 8,
  [int]$WorkerFixedDelayMs = 100,
  [string]$SshHost = "152.136.167.239"
)

$ErrorActionPreference = "Stop"

$workerEnabled = if ($Mode -eq "async" -or $Mode -eq "buffered_sync") { "true" } else { "false" }

$remoteScript = @"
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
path = Path('/home/ubuntu/sgcc-trust-data-space/.server.env.8node')
text = path.read_text(encoding='utf-8').splitlines()
updates = {
    'SGCC_ONCHAIN_MODE': '$Mode',
    'SGCC_ONCHAIN_WORKER_ENABLED': '$workerEnabled',
    'SGCC_ONCHAIN_WORKER_BATCH_SIZE': '$WorkerBatchSize',
    'SGCC_ONCHAIN_ANCHOR_AGGREGATE_SIZE': '$AnchorAggregateSize',
    'SGCC_ONCHAIN_SUBMIT_PARALLELISM': '$SubmitParallelism',
    'SGCC_ONCHAIN_WORKER_FIXED_DELAY_MS': '$WorkerFixedDelayMs',
}
seen = set()
out = []
for line in text:
    if line.startswith('export '):
        key = line[len('export '):].split('=', 1)[0]
        if key in updates:
            out.append(f"export {key}='{updates[key]}'")
            seen.add(key)
            continue
    out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f"export {key}='{value}'")
path.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY
cd /home/ubuntu/sgcc-trust-data-space/backend/platform-api
sudo systemctl stop sgcc-platform-api-8node.service 2>/dev/null || true
sudo systemctl reset-failed sgcc-platform-api-8node.service 2>/dev/null || true
pkill -f 'platform-api-0.1.0.jar' || true
rm -f /tmp/sgcc-platform-8node.log
sudo systemd-run \
  --unit=sgcc-platform-api-8node \
  --property=WorkingDirectory=/home/ubuntu/sgcc-trust-data-space/backend/platform-api \
  --property=User=ubuntu \
  /bin/bash -lc 'set -a; . /home/ubuntu/sgcc-trust-data-space/.server.env.8node; set +a; exec java -Xms256m -Xmx1024m -jar target/platform-api-0.1.0.jar >>/tmp/sgcc-platform-8node.log 2>&1'
for i in `$(seq 1 40); do
  if curl -fsS http://127.0.0.1:8089/api/demo/health >/dev/null 2>&1; then
    curl -fsS http://127.0.0.1:8089/api/demo/health
    exit 0
  fi
  sleep 2
done
tail -n 160 /tmp/sgcc-platform-8node.log >&2
exit 1
"@

$tmpScript = Join-Path $env:TEMP "codex_set_8node_onchain_mode.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
