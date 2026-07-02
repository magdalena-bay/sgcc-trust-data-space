[CmdletBinding()]
param(
  [string]$Cid,
  [string]$ServerHost = "152.136.167.239",
  [string]$RemoteGateway = "http://127.0.0.1:8080",
  [string]$IpfsExe = "D:\evermore\IPFS\IPFS Desktop\resources\app.asar.unpacked\node_modules\kubo\kubo\ipfs.exe"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Cid)) {
  throw "请传入 -Cid，例如：-Cid QmZNonw5SeyFojpqfAQ8UpfdUBfgRh1S6qzX3A6pKAW5yt"
}

if (-not (Test-Path $IpfsExe)) {
  throw "未找到本机 IPFS Desktop 自带的 ipfs 可执行文件：$IpfsExe"
}

$tmpDir = Join-Path $env:TEMP "sgcc-ipfs-import"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$tmpFile = Join-Path $tmpDir "$Cid.json"

Write-Host "从服务器拉取 CID 内容到本机临时文件..."
$remoteCommand = "curl -s $RemoteGateway/ipfs/$Cid | base64 -w0"
$base64Content = ssh $ServerHost $remoteCommand
if ([string]::IsNullOrWhiteSpace($base64Content)) {
  throw "服务器返回的 base64 内容为空"
}
$bytes = [System.Convert]::FromBase64String($base64Content.Trim())
[System.IO.File]::WriteAllBytes($tmpFile, $bytes)

if (-not (Test-Path $tmpFile)) {
  throw "临时文件未生成：$tmpFile"
}

$fileInfo = Get-Item $tmpFile
if ($fileInfo.Length -le 0) {
  throw "拉取到的临时文件为空：$tmpFile"
}

Write-Host "导入到本机 IPFS Desktop 节点..."
$addedCid = & $IpfsExe add -Q $tmpFile
if ($LASTEXITCODE -ne 0) {
  throw "ipfs add 执行失败"
}

if ($addedCid -ne $Cid) {
  throw "导入后的 CID 与目标不一致。目标=$Cid 实际=$addedCid"
}

Write-Host "固定到本机节点..."
& $IpfsExe pin add $Cid | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw "ipfs pin add 执行失败"
}

Write-Host ""
Write-Host "导入成功。"
Write-Host "CID: $Cid"
Write-Host "临时文件: $tmpFile"
Write-Host ""
Write-Host "现在你可以在 IPFS Desktop 中这样看："
Write-Host "1. 打开 IPFS Desktop"
Write-Host "2. 进入 Files 或 Explore"
Write-Host "3. 搜索或打开 CID: $Cid"
Write-Host "4. 如果界面仍未刷新，先关闭再重新打开一次 IPFS Desktop，然后再查看"
