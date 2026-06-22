$ErrorActionPreference = "Stop"

$RemoteHost = "152.136.167.239"
$RemoteRepoDir = "/home/ubuntu/sgcc-trust-data-space-sync/sgcc-trust-data-space"

$repoRoot = Split-Path -Parent $PSScriptRoot
$syncScript = Join-Path $PSScriptRoot "sync_frontend_dist_to_backend.ps1"
$archivePath = Join-Path $repoRoot "..\tmp\sgcc-trust-data-space-frontend-deploy.tar.gz"
$archivePath = [System.IO.Path]::GetFullPath($archivePath)

Write-Host "[1/5] Building frontend and syncing static assets into platform-api..."
powershell -ExecutionPolicy Bypass -File $syncScript

Write-Host "[2/5] Creating deployment archive..."
if (Test-Path $archivePath) {
  Remove-Item -LiteralPath $archivePath -Force
}
tar -C $repoRoot `
  --exclude='.git' `
  --exclude='frontend/user-web/node_modules' `
  --exclude='frontend/user-web/dist' `
  --exclude='backend/platform-api/target' `
  --exclude='services/privacy-service/.venv' `
  --exclude='__pycache__' `
  -czf $archivePath .

Write-Host "[3/5] Uploading archive to server..."
scp $archivePath "${RemoteHost}:/tmp/sgcc-trust-data-space-frontend-deploy.tar.gz"

Write-Host "[4/5] Extracting code, rebuilding platform-api, and restarting 8088..."
$remoteDeployScript = @'
set -euo pipefail

REPO_DIR="/home/ubuntu/sgcc-trust-data-space-sync/sgcc-trust-data-space"
APP_DIR="$REPO_DIR/backend/platform-api"
ENV_FILE="$REPO_DIR/.server.env"

cd "$REPO_DIR"
tar -xzf /tmp/sgcc-trust-data-space-frontend-deploy.tar.gz -C "$REPO_DIR"
rm -f /tmp/sgcc-trust-data-space-frontend-deploy.tar.gz

cd "$APP_DIR"
mvn -q -DskipTests package
sudo systemctl stop sgcc-platform-api.service 2>/dev/null || true
sudo systemctl reset-failed sgcc-platform-api.service 2>/dev/null || true
pkill -f 'platform-api-0.1.0.jar' || true
sleep 2
rm -f /tmp/sgcc-platform-live.log

# Run as a transient systemd unit so 8088 survives the deploy SSH session
# without becoming a permanent auto-start service.
sudo systemd-run \
  --unit=sgcc-platform-api \
  --uid=ubuntu \
  --gid=ubuntu \
  --working-directory="$APP_DIR" \
  --property=Type=simple \
  --property=Restart=no \
  /bin/bash -lc "if [ -f '$ENV_FILE' ]; then set -a; . '$ENV_FILE'; set +a; fi; exec java -Xms256m -Xmx1024m -jar target/platform-api-0.1.0.jar >>/tmp/sgcc-platform-live.log 2>&1"

for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8088/api/demo/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS http://127.0.0.1:8088/api/demo/health >/dev/null 2>&1; then
  echo "platform-api did not become healthy after deploy" >&2
  sudo systemctl status sgcc-platform-api.service --no-pager -l >&2 || true
  tail -n 200 /tmp/sgcc-platform-live.log >&2 || true
  exit 1
fi

curl -fsS http://127.0.0.1:8088/api/demo/health
echo
curl -fsS http://127.0.0.1:8088/api/demo/system-status
echo
curl -fsS http://127.0.0.1:8088/ | sed -n '1,5p'
'@
$remoteDeployScript | ssh $RemoteHost "tr -d '\r' | bash -s"

Write-Host "[5/5] Verifying platform-api is still alive after the deploy session exits..."
Start-Sleep -Seconds 8
$remoteRecheckScript = @'
set -euo pipefail
systemctl status sgcc-platform-api.service --no-pager -l | sed -n '1,40p'
echo '---HEALTH-RECHECK---'
curl -fsS http://127.0.0.1:8088/api/demo/health
echo
'@
$remoteRecheckScript | ssh $RemoteHost "tr -d '\r' | bash -s"

Write-Host "[5/5] Done."
Write-Host "Open: http://${RemoteHost}:8088/"
