param(
  [string]$SshHost = "152.136.167.239",
  [string]$SourceRoot = "/home/ubuntu/webase-front-instances",
  [string]$TargetRoot = "/home/ubuntu/webase-front-instances-8node",
  [string]$DataRoot = "/home/ubuntu/platform-infra/data/webase-8node",
  [switch]$Prepare,
  [switch]$Start,
  [switch]$Stop
)

$ErrorActionPreference = "Stop"

$remoteScript = @'
set -euo pipefail

SOURCE_ROOT="__SOURCE_ROOT__"
TARGET_ROOT="__TARGET_ROOT__"
DATA_ROOT="__DATA_ROOT__"
DO_PREPARE="__DO_PREPARE__"
DO_START="__DO_START__"
DO_STOP="__DO_STOP__"

copy_instance() {
  local name="$1"
  local port="$2"
  local peer="$3"
  local src="$SOURCE_ROOT/$name"
  local dst="$TARGET_ROOT/$name"
  local data_dir="$DATA_ROOT/$name"

  rm -rf "$dst"
  mkdir -p "$TARGET_ROOT" "$data_dir"
  cp -a "$src" "$dst"
  sed -i "s#^  port: .*#  port: $port#" "$dst/conf/application.yml"
  sed -i "s#^  peers: .*#  peers: ['127.0.0.1:$peer']#" "$dst/conf/application.yml"
  sed -i "s#jdbc:h2:file:/home/ubuntu/platform-infra/data/webase/$name/webasefront#jdbc:h2:file:$data_dir/webasefront#" "$dst/conf/application.yml"
}

start_instance() {
  local name="$1"
  local dst="$TARGET_ROOT/$name"
  local log="/tmp/webase-front-${name}-8node.log"
  pkill -f "$dst" || true
  cd "$dst"
  setsid nohup /bin/bash -lc 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64; exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Xms256m -Xmx768m -Xmn128m -Xss512k -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -Djava.library.path=conf -cp "conf/:apps/*:lib/*" com.webank.webase.front.Application >>"'"$log"'" 2>&1' >/dev/null 2>&1 &
}

stop_instance() {
  local name="$1"
  local dst="$TARGET_ROOT/$name"
  pkill -f "$dst" || true
}

if [ "$DO_PREPARE" = "true" ]; then
  copy_instance qingdao 5110 21200
  copy_instance weifang 5111 21400
  copy_instance relay 5112 21600
fi

if [ "$DO_STOP" = "true" ]; then
  stop_instance qingdao
  stop_instance weifang
  stop_instance relay
fi

if [ "$DO_START" = "true" ]; then
  start_instance qingdao
  start_instance weifang
  start_instance relay
  sleep 8
fi

echo "---webase8-layout"
find "$TARGET_ROOT" -maxdepth 2 -type d | sed -n '1,120p'
echo "---webase8-ports"
ss -ltnp | grep -E ':5110|:5111|:5112' || true
echo "---webase8-process"
ps -ef | grep -E 'webase-front-instances-8node' | grep -v grep || true
'@

$remoteScript = $remoteScript.Replace("__SOURCE_ROOT__", $SourceRoot)
$remoteScript = $remoteScript.Replace("__TARGET_ROOT__", $TargetRoot)
$remoteScript = $remoteScript.Replace("__DATA_ROOT__", $DataRoot)
$remoteScript = $remoteScript.Replace("__DO_PREPARE__", $(if ($Prepare) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__DO_START__", $(if ($Start) { "true" } else { "false" }))
$remoteScript = $remoteScript.Replace("__DO_STOP__", $(if ($Stop) { "true" } else { "false" }))

$tmpScript = Join-Path $env:TEMP "codex_prepare_three_8node_webase.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
