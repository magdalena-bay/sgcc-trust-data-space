param(
  [string]$SshHost = "152.136.167.239",
  [string]$RemoteRoot = "/home/ubuntu/blockchain/fisco-8node",
  [switch]$Build,
  [switch]$StartQingdao,
  [switch]$StartWeifang,
  [switch]$StartRelay
)

$ErrorActionPreference = "Stop"

$remoteScript = @'
set -euo pipefail

REMOTE_ROOT="__REMOTE_ROOT__"
BUILD_FLAG="__BUILD_FLAG__"
START_QINGDAO="__START_QINGDAO__"
START_WEIFANG="__START_WEIFANG__"
START_RELAY="__START_RELAY__"

TOOLS_DIR="/home/ubuntu/blockchain/tools"
BINARY_PATH="/home/ubuntu/blockchain/fisco/qingdao/127.0.0.1/fisco-bcos"

mkdir -p "$REMOTE_ROOT"

build_chain() {
  local name="$1"
  local chain_id="$2"
  local rpc_start="$3"
  local p2p_start="$4"
  local target_dir="$REMOTE_ROOT/$name"

  rm -rf "$target_dir"
  cd "$TOOLS_DIR"
  bash build_chain.local.sh -I "$chain_id" -p "$p2p_start,$rpc_start" -l "127.0.0.1:8" -o "$target_dir" -e "$BINARY_PATH"
  find "$target_dir/127.0.0.1" -maxdepth 2 -name config.genesis -type f -print0 | while IFS= read -r -d '' genesis; do
    sed -i "s/^    auth_admin_account=.*/    auth_admin_account=0x0000000000000000000000000000000000000000/" "$genesis"
  done
}

if [ "$BUILD_FLAG" = "true" ]; then
  build_chain qingdao qingdao 21200 30480
  build_chain weifang weifang 21400 30580
  build_chain relay relay 21600 30680
fi

if [ "$START_QINGDAO" = "true" ]; then
  cd "$REMOTE_ROOT/qingdao/127.0.0.1"
  bash start_all.sh
fi

if [ "$START_WEIFANG" = "true" ]; then
  cd "$REMOTE_ROOT/weifang/127.0.0.1"
  bash start_all.sh
fi

if [ "$START_RELAY" = "true" ]; then
  cd "$REMOTE_ROOT/relay/127.0.0.1"
  bash start_all.sh
fi

echo "---layout"
find "$REMOTE_ROOT" -maxdepth 3 -type d | sed -n '1,200p'
echo "---ports"
ss -ltn | grep -E ':21200|:21201|:21202|:21203|:21204|:21205|:21206|:21207|:21400|:21401|:21402|:21403|:21404|:21405|:21406|:21407|:21600|:21601|:21602|:21603|:21604|:21605|:21606|:21607' || true
'@

$remoteScript = $remoteScript.Replace("__REMOTE_ROOT__", $RemoteRoot)
$remoteScript = $remoteScript.Replace("__BUILD_FLAG__", $(if ($Build) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__START_QINGDAO__", $(if ($StartQingdao) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__START_WEIFANG__", $(if ($StartWeifang) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__START_RELAY__", $(if ($StartRelay) { "true" } else { "false" }))

$tmpScript = Join-Path $env:TEMP "codex_prepare_three_8node_chains.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
