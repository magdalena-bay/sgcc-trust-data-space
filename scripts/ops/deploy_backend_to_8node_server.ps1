param(
  [string]$RemoteHost = "152.136.167.239",
  [string]$RemoteRepoDir = "/home/ubuntu/sgcc-trust-data-space"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$archivePath = Join-Path $repoRoot "..\tmp\sgcc-trust-data-space-backend-8node-deploy.tar.gz"
$archivePath = [System.IO.Path]::GetFullPath($archivePath)

Write-Host "[1/4] Creating backend-only deployment archive..."
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
  -czf $archivePath `
  backend `
  contracts `
  docs `
  scripts `
  chain-config `
  README.md `
  .gitignore

Write-Host "[2/4] Uploading archive to server..."
scp $archivePath "${RemoteHost}:/tmp/sgcc-trust-data-space-backend-8node-deploy.tar.gz"

Write-Host "[3/4] Extracting code, rebuilding platform-api, and restarting 8089..."
$remoteDeployScript = @'
set -euo pipefail

REPO_DIR="/home/ubuntu/sgcc-trust-data-space"
APP_DIR="$REPO_DIR/backend/platform-api"

cd "$REPO_DIR"
tar -xzf /tmp/sgcc-trust-data-space-backend-8node-deploy.tar.gz -C "$REPO_DIR"
rm -f /tmp/sgcc-trust-data-space-backend-8node-deploy.tar.gz
find "$REPO_DIR/scripts/ops" -maxdepth 1 -type f -name '*.sh' -exec chmod 755 {} \;

cd "$APP_DIR"
mvn -q -DskipTests package
'@
$remoteDeployScript | ssh $RemoteHost "tr -d '\r' | bash -s"

powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "set_8node_onchain_mode.ps1") -SshHost $RemoteHost

Write-Host "[4/4] Verifying 8089 health after backend deploy..."
$recheckScript = @'
set -euo pipefail
curl -fsS http://127.0.0.1:8089/api/demo/health
echo
free -h
'@
$recheckScript | ssh $RemoteHost "tr -d '\r' | bash -s"

Write-Host "[4/4] Done."
