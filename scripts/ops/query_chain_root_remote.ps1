param(
  [ValidateSet("qingdao", "weifang", "relay")]
  [string]$ChainName = "qingdao",
  [string]$RegionRoot = "qingdao",
  [string]$RootDataId,
  [string]$SshHost = "152.136.167.239",
  [string]$RemoteRepoDir = "/home/ubuntu/sgcc-trust-data-space",
  [string]$ServerEnvFile
)

$ErrorActionPreference = "Stop"

if (-not $RootDataId) {
  $RootDataId = "__VERKLE_ROOT__:$RegionRoot"
}

if (-not $ServerEnvFile) {
  $ServerEnvFile = "$RemoteRepoDir/.server.env.8node"
}

$python = @"
import base64
import json
import sys
import urllib.request

CHAIN = "$ChainName"
ROOT_DATA_ID = "$RootDataId"
REMOTE_REPO_DIR = "$RemoteRepoDir"
SERVER_ENV_FILE = "$ServerEnvFile"

CHAIN_CONFIG = {
    "qingdao": {
        "base": "http://127.0.0.1:5110/WeBASE-Front",
        "contract": "0xafcca7e2ca495ca06a4715ef4ce6457958c42a6e",
    },
    "weifang": {
        "base": "http://127.0.0.1:5111/WeBASE-Front",
        "contract": "0xc8ead4b26b2c6ac14c9fd90d9684c9bc2cc40085",
    },
    "relay": {
        "base": "http://127.0.0.1:5112/WeBASE-Front",
        "contract": "0x600e41f494cbeed1936d5e0a293aee0ab1746c7b",
    },
}

CONTRACT_FILE = REMOTE_REPO_DIR + "/backend/platform-api/src/main/resources/contracts/SgccTrustAnchor.sol"

def load_service_user():
    import os

    env_value = os.getenv("SGCC_CHAIN_SERVICE_ADDRESS", "").strip()
    if env_value:
        return env_value

    try:
        with open(SERVER_ENV_FILE, "r", encoding="utf-8") as f:
            for raw_line in f:
                line = raw_line.strip()
                if not line or line.startswith("#"):
                    continue
                if line.startswith("export "):
                    line = line[len("export "):]
                if not line.startswith("SGCC_CHAIN_SERVICE_ADDRESS="):
                    continue
                return line.split("=", 1)[1].strip().strip("'\"")
    except FileNotFoundError:
        pass

    raise SystemExit(
        "SGCC_CHAIN_SERVICE_ADDRESS is required. Export it or place it in " + SERVER_ENV_FILE
    )

SERVICE_USER = load_service_user()

def http_json(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())

cfg = CHAIN_CONFIG[CHAIN]
with open(CONTRACT_FILE, "rb") as f:
    solidity_base64 = base64.b64encode(f.read()).decode()

compile_payload = {
    "groupId": "group0",
    "contractName": "SgccTrustAnchor",
    "solidityBase64": solidity_base64,
}
compile_resp = http_json(cfg["base"] + "/contract/contractCompile", compile_payload)
abi = json.loads(compile_resp["contractAbi"])

read_payload = {
    "groupId": "group0",
    "contractName": "SgccTrustAnchor",
    "contractAddress": cfg["contract"],
    "contractAbi": abi,
    "funcName": "getAnchor",
    "funcParam": [ROOT_DATA_ID],
    "user": SERVICE_USER,
    "useAes": False,
}

result = http_json(cfg["base"] + "/trans/handle", read_payload)

summary = {
    "chainName": CHAIN,
    "webaseBaseUrl": cfg["base"],
    "contractAddress": cfg["contract"],
    "serviceUser": SERVICE_USER,
    "rootDataId": ROOT_DATA_ID,
    "rawResult": result,
}

if isinstance(result, list) and len(result) >= 9:
    summary["parsed"] = {
        "region": result[0],
        "cid": result[1],
        "packageHash": result[2],
        "policyHash": result[3],
        "ownerDid": result[4],
        "dataType": result[5],
        "root": result[6],
        "createdAt": result[7],
        "exists": result[8],
    }

print(json.dumps(summary, ensure_ascii=False, indent=2))
"@

$tmpSuffix = [Guid]::NewGuid().ToString("N")
$tmpPy = Join-Path $env:TEMP ("codex_query_chain_root_" + $tmpSuffix + ".py")
$tmpRunner = Join-Path $env:TEMP ("codex_query_chain_root_" + $tmpSuffix + ".sh")

[System.IO.File]::WriteAllText($tmpPy, $python, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($tmpRunner, @'
#!/usr/bin/env bash
set -euo pipefail
python3 /tmp/codex_query_chain_root.py
rm -f /tmp/codex_query_chain_root.py /tmp/codex_query_chain_root.sh /tmp/codex_query_chain_root.clean.sh
'@, [System.Text.ASCIIEncoding]::new())

try {
  scp $tmpPy "${SshHost}:/tmp/codex_query_chain_root.py" | Out-Null
  scp $tmpRunner "${SshHost}:/tmp/codex_query_chain_root.sh" | Out-Null
  ssh $SshHost "tr -d '\r' </tmp/codex_query_chain_root.sh >/tmp/codex_query_chain_root.clean.sh && chmod +x /tmp/codex_query_chain_root.clean.sh && /tmp/codex_query_chain_root.clean.sh"
} finally {
  Remove-Item -LiteralPath $tmpPy -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $tmpRunner -Force -ErrorAction SilentlyContinue
}
