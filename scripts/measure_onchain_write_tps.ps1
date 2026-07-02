param(
  [string]$ApiBase = "http://127.0.0.1:8089",
  [int]$Count = 200,
  [ValidateSet("anchor", "access", "anchor_digest", "upload_checkpoint", "upload_legacy")]
  [string]$EventType = "anchor",
  [ValidateSet("qingdao", "weifang", "relay")]
  [string]$ChainName = "qingdao",
  [int]$BatchSize = 100,
  [int]$Concurrency = 1
)

$ErrorActionPreference = "Stop"

$body = @{
  chainName = $ChainName
  eventType = $EventType
  count = $Count
  batchSize = $BatchSize
  concurrency = $Concurrency
} | ConvertTo-Json

Write-Host "[1/2] Calling $ApiBase/api/demo/benchmark/onchain ..."
$response = Invoke-RestMethod `
  -Method Post `
  -Uri "$ApiBase/api/demo/benchmark/onchain" `
  -ContentType "application/json" `
  -Body $body

Write-Host "[2/2] Benchmark result:"
$response | ConvertTo-Json -Depth 6
