param(
  [string]$SshHost = "152.136.167.239",
  [string]$RemoteRepoDir = "/home/ubuntu/sgcc-trust-data-space",
  [string]$ApiBase = "http://127.0.0.1:8088",
  [ValidateSet("qingdao", "weifang")]
  [string]$Region = "qingdao",
  [string]$DataId
)

$ErrorActionPreference = "Stop"

if (-not $DataId) {
  $suffix = Get-Date -Format "yyyyMMddHHmmss"
  $DataId = if ($Region -eq "qingdao") { "DQA$($suffix)QD" } else { "DQA$($suffix)WF" }
}

$remoteScript = @'
set -euo pipefail
cd "__REMOTE_REPO_DIR__"
python3 - <<'PY'
import json
import sys
import urllib.request

API_BASE = "__API_BASE__"
REGION = "__REGION__"
DATA_ID = "__DATA_ID__"

PRESETS = {
    "qingdao": {
        "ownerDid": "did:weid:qingdao:4001",
        "dataType": "load_curve",
        "policyOrg": "SGCC_dispatch_center",
        "policyRole": "load_analyst",
        "policyGrantStatus": "valid",
        "plaintext": "time,load\n10:00,1400\n10:15,1420\n10:30,1410",
    },
    "weifang": {
        "ownerDid": "did:weid:weifang:5001",
        "dataType": "generation_curve",
        "policyOrg": "SGCC_dispatch_center",
        "policyRole": "dispatch_analyst",
        "policyGrantStatus": "valid",
        "plaintext": "time,power\n14:00,920\n14:15,950\n14:30,980",
    },
}

preset = PRESETS[REGION]

def request_json(method, path, body=None):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API_BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))

print("[1/7] Checking platform-api health...")
health = request_json("GET", "/api/demo/health")

print("[2/7] Checking system status...")
system_status = request_json("GET", "/api/demo/system-status")

required = [
    system_status["components"]["platformApi"],
    system_status["components"]["privacyService"],
    system_status["components"]["mysql"],
    system_status["components"]["redis"],
    system_status["components"]["postgres"],
    system_status["components"]["ipfsApi"],
    system_status["components"]["ipfsGateway"],
    system_status["chainStatus"]["qingdao"],
    system_status["chainStatus"]["weifang"],
    system_status["chainStatus"]["relay"],
]
if not all(required):
    raise SystemExit("system-status contains unhealthy dependency, stop smoke test first")

print("[3/7] Uploading test resource...")
upload = request_json("POST", "/api/demo/upload", {
    "dataId": DATA_ID,
    "region": REGION,
    "ownerDid": preset["ownerDid"],
    "dataType": preset["dataType"],
    "policyOrg": preset["policyOrg"],
    "policyRole": preset["policyRole"],
    "policyGrantStatus": preset["policyGrantStatus"],
    "plaintext": preset["plaintext"],
})

print("[4/7] Fetching resource detail and Verkle views...")
detail = request_json("GET", f"/api/demo/resources/{DATA_ID}")
verkle = request_json("GET", f"/api/demo/resources/{DATA_ID}/verkle")
audit = request_json("GET", f"/api/demo/resources/{DATA_ID}/verkle-audit")

print("[5/7] Testing allowed access...")
allowed = request_json("POST", "/api/demo/access", {
    "dataId": DATA_ID,
    "requesterOrg": preset["policyOrg"],
    "requesterRole": preset["policyRole"],
    "requesterGrantStatus": preset["policyGrantStatus"],
})

print("[6/7] Testing denied access...")
denied = request_json("POST", "/api/demo/access", {
    "dataId": DATA_ID,
    "requesterOrg": preset["policyOrg"],
    "requesterRole": "guest_viewer",
    "requesterGrantStatus": preset["policyGrantStatus"],
})

failures = []
if health.get("status") != "ok":
    failures.append("platform-api health is not ok")
if not audit.get("overallPassed"):
    failures.append("verkle-audit overallPassed=false")
if not allowed.get("granted") or not allowed.get("verified"):
    failures.append("allowed access did not return granted=true and verified=true")
if denied.get("granted"):
    failures.append("denied access unexpectedly returned granted=true")
if not denied.get("verified"):
    failures.append("denied access did not preserve verified=true")

summary = {
    "apiBase": API_BASE,
    "region": REGION,
    "dataId": DATA_ID,
    "upload": {
        "cid": upload.get("cid"),
        "hdValue": upload.get("hdValue"),
        "root": upload.get("root"),
        "relayRoot": upload.get("relayRoot"),
    },
    "detail": {
        "policyExpr": detail.get("policyExpr"),
        "packageHash": detail.get("packageHash"),
        "root": detail.get("root"),
        "relayRoot": detail.get("relayRoot"),
    },
    "verkle": {
        "proofKey": verkle.get("proofKey"),
        "chainAnchorExists": verkle.get("chainAnchorExists"),
        "chainRoot": verkle.get("chainRoot"),
    },
    "audit": {
        "overallPassed": audit.get("overallPassed"),
        "redisProofExists": audit.get("redisProofExists"),
        "redisProofMatchesRebuilt": audit.get("redisProofMatchesRebuilt"),
        "rebuiltRootMatchesMysqlRoot": audit.get("rebuiltRootMatchesMysqlRoot"),
        "rebuiltRootMatchesRegionChainRoot": audit.get("rebuiltRootMatchesRegionChainRoot"),
        "mysqlRelayRootMatchesRelayChainRoot": audit.get("mysqlRelayRootMatchesRelayChainRoot"),
        "proofVerifiesAgainstMysqlRoot": audit.get("proofVerifiesAgainstMysqlRoot"),
        "proofVerifiesAgainstRegionChainRoot": audit.get("proofVerifiesAgainstRegionChainRoot"),
        "proofVerifiesAgainstRelayChainRoot": audit.get("proofVerifiesAgainstRelayChainRoot"),
    },
    "allowedAccess": {
        "granted": allowed.get("granted"),
        "verified": allowed.get("verified"),
        "message": allowed.get("message"),
    },
    "deniedAccess": {
        "granted": denied.get("granted"),
        "verified": denied.get("verified"),
        "message": denied.get("message"),
    },
    "failures": failures,
}

print("[7/7] Smoke test summary:")
print(json.dumps(summary, ensure_ascii=False, indent=2))

if failures:
    raise SystemExit("backend smoke test failed: " + "; ".join(failures))
PY
'@

$remoteScript = $remoteScript.Replace("__REMOTE_REPO_DIR__", $RemoteRepoDir)
$remoteScript = $remoteScript.Replace("__API_BASE__", $ApiBase)
$remoteScript = $remoteScript.Replace("__REGION__", $Region)
$remoteScript = $remoteScript.Replace("__DATA_ID__", $DataId)

$tmpScript = Join-Path $env:TEMP "codex_verkle_smoke_remote.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
