param(
  [ValidateSet("anchor", "access", "anchor_digest", "upload_checkpoint", "upload_legacy")]
  [string]$EventType = "anchor_digest",
  [ValidateSet("qingdao", "weifang", "relay")]
  [string]$ChainName = "qingdao",
  [int]$Count = 10000,
  [int]$BatchSize = 10000,
  [int]$Concurrency = 1,
  [int]$MaxAttempts = 3,
  [string]$SshHost = "152.136.167.239"
)

$ErrorActionPreference = "Stop"

$python = @"
import json, sys, time, traceback, urllib.request

BODY = json.dumps({
    "chainName": "$ChainName",
    "eventType": "$EventType",
    "count": $Count,
    "batchSize": $BatchSize,
    "concurrency": $Concurrency,
}).encode()
URL = "http://127.0.0.1:8089/api/demo/benchmark/onchain"
HEALTH_URL = "http://127.0.0.1:8089/api/demo/health"
MAX_ATTEMPTS = $MaxAttempts

def http_get(url, timeout=30):
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return resp.read().decode()

def post_once():
    req = urllib.request.Request(
        URL,
        data=BODY,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        return resp.read().decode()

for attempt in range(1, MAX_ATTEMPTS + 1):
    try:
        http_get(HEALTH_URL, timeout=15)
        print(post_once())
        sys.exit(0)
    except Exception:
        traceback.print_exc()
        if attempt >= MAX_ATTEMPTS:
            sys.exit(1)
        time.sleep(min(5 * attempt, 15))
"@

$tmpSuffix = [Guid]::NewGuid().ToString("N")
$tmpPy = Join-Path $env:TEMP ("codex_measure_onchain_remote_" + $tmpSuffix + ".py")
if (Test-Path $tmpPy) {
  Remove-Item -LiteralPath $tmpPy -Force -ErrorAction SilentlyContinue
}
[System.IO.File]::WriteAllText($tmpPy, $python, [System.Text.UTF8Encoding]::new($false))
$tmpRunner = Join-Path $env:TEMP ("codex_measure_onchain_remote_" + $tmpSuffix + ".sh")
$remoteRunner = @'
#!/usr/bin/env bash
set -euo pipefail
tmp_py="/tmp/codex_measure_onchain_remote.py"
python3 "$tmp_py"
rc=$?
if [ $rc -ne 0 ]; then
  echo "--- platform-api log tail ---" >&2
  tail -n 120 /tmp/sgcc-platform-8node.log >&2 || true
fi
rm -f "$tmp_py" /tmp/codex_measure_onchain_remote.sh
exit $rc
'@
if (Test-Path $tmpRunner) {
  Remove-Item -LiteralPath $tmpRunner -Force -ErrorAction SilentlyContinue
}
[System.IO.File]::WriteAllText($tmpRunner, $remoteRunner, [System.Text.ASCIIEncoding]::new())
try {
  scp $tmpPy "${SshHost}:/tmp/codex_measure_onchain_remote.py" | Out-Null
  scp $tmpRunner "${SshHost}:/tmp/codex_measure_onchain_remote.sh" | Out-Null
  ssh $SshHost "tr -d '\r' </tmp/codex_measure_onchain_remote.sh >/tmp/codex_measure_onchain_remote.clean.sh && chmod +x /tmp/codex_measure_onchain_remote.clean.sh && /tmp/codex_measure_onchain_remote.clean.sh"
} finally {
  Remove-Item -LiteralPath $tmpPy -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $tmpRunner -Force -ErrorAction SilentlyContinue
}
