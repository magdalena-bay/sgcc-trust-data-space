#!/usr/bin/env bash
set -euo pipefail

CHAIN_NAME="${1:-qingdao}"
REGION_ROOT="${2:-$CHAIN_NAME}"
ROOT_DATA_ID="${3:-__VERKLE_ROOT__:${REGION_ROOT}}"
REMOTE_REPO_DIR="${REMOTE_REPO_DIR:-/home/ubuntu/sgcc-trust-data-space}"
SERVER_ENV_FILE="${SERVER_ENV_FILE:-${REMOTE_REPO_DIR}/.server.env.8node}"

case "$CHAIN_NAME" in
  qingdao)
    WEBASE_BASE="http://127.0.0.1:5110/WeBASE-Front"
    CONTRACT_ADDRESS="0xafcca7e2ca495ca06a4715ef4ce6457958c42a6e"
    ;;
  weifang)
    WEBASE_BASE="http://127.0.0.1:5111/WeBASE-Front"
    CONTRACT_ADDRESS="0xc8ead4b26b2c6ac14c9fd90d9684c9bc2cc40085"
    ;;
  relay)
    WEBASE_BASE="http://127.0.0.1:5112/WeBASE-Front"
    CONTRACT_ADDRESS="0x600e41f494cbeed1936d5e0a293aee0ab1746c7b"
    ;;
  *)
    echo "unsupported chain: $CHAIN_NAME" >&2
    echo "usage: ./query_chain_root.sh [qingdao|weifang|relay] [regionRoot]" >&2
    exit 1
    ;;
esac

SERVICE_USER="${SGCC_CHAIN_SERVICE_ADDRESS:-}"
if [[ -z "${SERVICE_USER}" && -f "${SERVER_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a
  . "${SERVER_ENV_FILE}"
  set +a
  SERVICE_USER="${SGCC_CHAIN_SERVICE_ADDRESS:-}"
fi
if [[ -z "${SERVICE_USER}" ]]; then
  echo "SGCC_CHAIN_SERVICE_ADDRESS is required. Export it or provide it via ${SERVER_ENV_FILE}" >&2
  exit 1
fi
CONTRACT_FILE="${REMOTE_REPO_DIR}/backend/platform-api/src/main/resources/contracts/SgccTrustAnchor.sol"

python3 - <<PY
import base64
import json
import urllib.request

webase_base = ${WEBASE_BASE@Q}
contract_address = ${CONTRACT_ADDRESS@Q}
service_user = ${SERVICE_USER@Q}
contract_file = ${CONTRACT_FILE@Q}
root_data_id = ${ROOT_DATA_ID@Q}
chain_name = ${CHAIN_NAME@Q}

def http_json(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())

with open(contract_file, "rb") as f:
    solidity_base64 = base64.b64encode(f.read()).decode()

compile_payload = {
    "groupId": "group0",
    "contractName": "SgccTrustAnchor",
    "solidityBase64": solidity_base64,
}
compile_resp = http_json(webase_base + "/contract/contractCompile", compile_payload)
abi = json.loads(compile_resp["contractAbi"])

payload = {
    "groupId": "group0",
    "contractName": "SgccTrustAnchor",
    "contractAddress": contract_address,
    "contractAbi": abi,
    "funcName": "getAnchor",
    "funcParam": [root_data_id],
    "user": service_user,
    "useAes": False,
}
result = http_json(webase_base + "/trans/handle", payload)

summary = {
    "chainName": chain_name,
    "webaseBaseUrl": webase_base,
    "contractAddress": contract_address,
    "serviceUser": service_user,
    "rootDataId": root_data_id,
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
PY
