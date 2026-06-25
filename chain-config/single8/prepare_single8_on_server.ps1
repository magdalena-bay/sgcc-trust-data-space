param(
  [string]$SshHost = "152.136.167.239",
  [string]$RemoteBase = "/home/ubuntu/blockchain/fisco/single8",
  [string]$RemoteToolsDir = "/home/ubuntu/blockchain/tools",
  [string]$BinaryPath = "/home/ubuntu/blockchain/fisco/qingdao/127.0.0.1/fisco-bcos",
  [int]$NodeCount = 8,
  [int]$P2pStart = 30380,
  [int]$RpcStart = 21000,
  [switch]$Build,
  [switch]$Start
)

$ErrorActionPreference = "Stop"

$remoteScript = @'
set -euo pipefail

REMOTE_BASE="__REMOTE_BASE__"
REMOTE_TOOLS_DIR="__REMOTE_TOOLS_DIR__"
BINARY_PATH="__BINARY_PATH__"
NODE_COUNT="__NODE_COUNT__"
P2P_START="__P2P_START__"
RPC_START="__RPC_START__"
BUILD_FLAG="__BUILD_FLAG__"
START_FLAG="__START_FLAG__"

echo "[1/6] Preflight check..."
test -d "$REMOTE_TOOLS_DIR"
test -x "$BINARY_PATH"
mkdir -p "$REMOTE_BASE"

echo "[2/6] Environment summary..."
hostname
echo "---"
nproc
echo "---"
free -h
echo "---"
ss -ltn | grep -E ':20200|:20400|:20600|:21000|:21001|:21002|:21003|:21004|:21005|:21006|:21007|:30380|:30381|:30382|:30383|:30384|:30385|:30386|:30387' || true

echo "[3/6] Write layout note..."
cat > "$REMOTE_BASE/README.single8.txt" <<EOF
single8 target layout
base=$REMOTE_BASE
nodeCount=$NODE_COUNT
p2pStart=$P2P_START
rpcStart=$RPC_START
EOF

echo "[4/6] Build flag: $BUILD_FLAG"
if [ "$BUILD_FLAG" = "true" ]; then
  rm -rf "$REMOTE_BASE/generated"
  cd "$REMOTE_TOOLS_DIR"
  bash build_chain.local.sh -p "$P2P_START,$RPC_START" -l "127.0.0.1:$NODE_COUNT" -o "$REMOTE_BASE/generated" -e "$BINARY_PATH"
fi

echo "[5/6] Start flag: $START_FLAG"
if [ "$START_FLAG" = "true" ]; then
  cd "$REMOTE_BASE/generated"
  bash start_all.sh
fi

echo "[6/6] Final check..."
find "$REMOTE_BASE" -maxdepth 3 -type d | sed -n '1,120p'
echo "---"
ss -ltn | grep -E ':21000|:21001|:21002|:21003|:21004|:21005|:21006|:21007' || true
'@

$remoteScript = $remoteScript.Replace("__REMOTE_BASE__", $RemoteBase)
$remoteScript = $remoteScript.Replace("__REMOTE_TOOLS_DIR__", $RemoteToolsDir)
$remoteScript = $remoteScript.Replace("__BINARY_PATH__", $BinaryPath)
$remoteScript = $remoteScript.Replace("__NODE_COUNT__", [string]$NodeCount)
$remoteScript = $remoteScript.Replace("__P2P_START__", [string]$P2pStart)
$remoteScript = $remoteScript.Replace("__RPC_START__", [string]$RpcStart)
$remoteScript = $remoteScript.Replace("__BUILD_FLAG__", $(if ($Build) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__START_FLAG__", $(if ($Start) { "true" } else { "false" }))

$tmpScript = Join-Path $env:TEMP "codex_prepare_single8_on_server.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
