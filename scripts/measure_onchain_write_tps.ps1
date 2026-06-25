param(
  [string]$SshHost = "152.136.167.239",
  [int]$Samples = 10,
  [ValidateSet("all", "anchor", "access")]
  [string]$Mode = "all"
)

$ErrorActionPreference = "Stop"

$remoteScript = @'
set -euo pipefail
python3 - <<'PY'
import json
import statistics
import time
import urllib.request

GROUP_ID = "group0"
SERVICE_ADDR = "0x1077c260eadd22145bdb2221be0033fd23a2504d"
SAMPLES = __SAMPLES__
MODE = "__MODE__"

ABI = [
    {"inputs": [], "stateMutability": "nonpayable", "type": "constructor"},
    {"inputs": [{"internalType": "string", "name": "dataId", "type": "string"}], "name": "getAnchor", "outputs": [{"internalType": "string", "name": "region", "type": "string"}, {"internalType": "string", "name": "cid", "type": "string"}, {"internalType": "string", "name": "packageHash", "type": "string"}, {"internalType": "string", "name": "policyHash", "type": "string"}, {"internalType": "string", "name": "ownerDid", "type": "string"}, {"internalType": "string", "name": "dataType", "type": "string"}, {"internalType": "string", "name": "root", "type": "string"}, {"internalType": "uint256", "name": "createdAt", "type": "uint256"}, {"internalType": "bool", "name": "exists", "type": "bool"}], "stateMutability": "view", "type": "function"},
    {"inputs": [{"internalType": "string", "name": "dataId", "type": "string"}, {"internalType": "string", "name": "requesterOrg", "type": "string"}, {"internalType": "string", "name": "requesterRole", "type": "string"}, {"internalType": "bool", "name": "verified", "type": "bool"}, {"internalType": "bool", "name": "granted", "type": "bool"}], "name": "recordAccess", "outputs": [], "stateMutability": "nonpayable", "type": "function"},
    {"inputs": [{"internalType": "string", "name": "dataId", "type": "string"}, {"internalType": "string", "name": "region", "type": "string"}, {"internalType": "string", "name": "cid", "type": "string"}, {"internalType": "string", "name": "packageHash", "type": "string"}, {"internalType": "string", "name": "policyHash", "type": "string"}, {"internalType": "string", "name": "ownerDid", "type": "string"}, {"internalType": "string", "name": "dataType", "type": "string"}, {"internalType": "string", "name": "root", "type": "string"}], "name": "anchorResource", "outputs": [], "stateMutability": "nonpayable", "type": "function"}
]

CHAINS = {
    "qingdao": ("http://127.0.0.1:5100/WeBASE-Front", "0x6546c3571f17858ea45575e7c6457dad03e53dbb"),
    "weifang": ("http://127.0.0.1:5101/WeBASE-Front", "0x37a44585bf1e9618fdb4c62c4c96189a07dd4b48"),
    "relay": ("http://127.0.0.1:5102/WeBASE-Front", "0x37a44585bf1e9618fdb4c62c4c96189a07dd4b48"),
}

def post_json(url, payload, timeout=120):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        text = resp.read().decode("utf-8")
        return json.loads(text) if text[:1] in "{[" else text

def summarize(latencies, sample_result):
    vals = sorted(latencies)
    p95_index = min(len(vals) - 1, max(0, int(round(len(vals) * 0.95)) - 1))
    return {
        "count": len(vals),
        "avg_ms": round(statistics.mean(vals), 2),
        "min_ms": round(min(vals), 2),
        "max_ms": round(max(vals), 2),
        "p95_ms": round(vals[p95_index], 2),
        "throughput_tps": round(1000 / statistics.mean(vals), 2),
        "sample_result": sample_result,
    }

def measure(chain_name, func_name, param_factory):
    base_url, contract_address = CHAINS[chain_name]
    latencies = []
    sample_result = None
    for i in range(SAMPLES):
        payload = {
            "groupId": GROUP_ID,
            "contractName": "SgccTrustAnchor",
            "contractAddress": contract_address,
            "contractAbi": ABI,
            "funcName": func_name,
            "funcParam": param_factory(i),
            "user": SERVICE_ADDR,
            "useAes": False,
        }
        t0 = time.perf_counter()
        result = post_json(base_url + "/trans/handle", payload)
        elapsed_ms = (time.perf_counter() - t0) * 1000
        latencies.append(elapsed_ms)
        if sample_result is None:
            sample_result = str(result)[:160]
        time.sleep(0.2)
    return summarize(latencies, sample_result)

summary = {
    "testScope": "blockchain_onchain_only",
    "note": "Only WeBASE /trans/handle write path measured. Excludes IPFS, MySQL, Redis, Verkle rebuild, decrypt and full upload orchestration.",
    "samples": SAMPLES,
    "results": {}
}

if MODE in ("all", "anchor"):
    summary["results"]["anchorResource"] = {
        "qingdao": measure(
            "qingdao",
            "anchorResource",
            lambda i: [f"TPCHAINQ{i}", "qingdao", f"cid-q-{i}", f"pkg-q-{i}", "policy-q", "did:weid:qingdao:4001", "load_curve", f"root-q-{i}"]
        ),
        "weifang": measure(
            "weifang",
            "anchorResource",
            lambda i: [f"TPCHAINW{i}", "weifang", f"cid-w-{i}", f"pkg-w-{i}", "policy-w", "did:weid:weifang:5001", "generation_curve", f"root-w-{i}"]
        ),
        "relay": measure(
            "relay",
            "anchorResource",
            lambda i: [f"TPCHAINR{i}", "relay", f"cid-r-{i}", f"pkg-r-{i}", "policy-r", "did:weid:relay:6001", "relay_anchor", f"root-r-{i}"]
        ),
    }

if MODE in ("all", "access"):
    summary["results"]["recordAccess"] = {
        "relay": measure(
            "relay",
            "recordAccess",
            lambda i: [f"TPACCESS{i}", "SGCC_dispatch_center", "load_analyst", True, True]
        )
    }

print(json.dumps(summary, ensure_ascii=False, indent=2))
PY
'@

$remoteScript = $remoteScript.Replace("__SAMPLES__", [string]$Samples)
$remoteScript = $remoteScript.Replace("__MODE__", $Mode)

$tmpScript = Join-Path $env:TEMP "codex_measure_onchain_write_tps.sh"
Set-Content -LiteralPath $tmpScript -Value $remoteScript -Encoding UTF8
try {
  Get-Content -LiteralPath $tmpScript -Raw | ssh -o ConnectTimeout=10 $SshHost "tr -d '\r' | bash -s"
} finally {
  Remove-Item -LiteralPath $tmpScript -Force -ErrorAction SilentlyContinue
}
