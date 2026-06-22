$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend\user-web"
$backendStaticDir = Join-Path $repoRoot "backend\platform-api\src\main\resources\static"

Write-Host "[1/4] Building frontend dist..."
Push-Location $frontendDir
try {
  npm run build
} finally {
  Pop-Location
}

Write-Host "[2/4] Preparing backend static directory..."
if (Test-Path $backendStaticDir) {
  Remove-Item -LiteralPath $backendStaticDir -Recurse -Force
}
New-Item -ItemType Directory -Path $backendStaticDir | Out-Null

Write-Host "[3/4] Copying dist into platform-api static resources..."
Copy-Item -Path (Join-Path $frontendDir "dist\*") -Destination $backendStaticDir -Recurse -Force

Write-Host "[4/4] Frontend assets are ready for platform-api packaging."
Get-ChildItem -LiteralPath $backendStaticDir

Write-Host ""
Write-Host "Next step:"
Write-Host "  Rebuild backend/platform-api and redeploy the jar to make 8088 serve the latest frontend."
