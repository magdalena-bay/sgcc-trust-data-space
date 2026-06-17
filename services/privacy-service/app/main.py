import base64
import hashlib
import json
import os
from dataclasses import dataclass
from typing import Dict, List, Optional, Union

from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes
from fastapi import FastAPI
from pydantic import BaseModel, Field


app = FastAPI(title="sgcc privacy-service", version="0.1.0")


def _b64encode(raw: bytes) -> str:
    return base64.b64encode(raw).decode("utf-8")


def _b64decode(raw: str) -> bytes:
    return base64.b64decode(raw.encode("utf-8"))


def _sha256_hex(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _canonical_json(value: dict) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _master_key() -> bytes:
    # The demo master key is only used to wrap the per-resource DEK so the end-to-end
    # flow can run before a true MA-CP-ABE implementation is introduced.
    configured = os.getenv("SGCC_PRIVACY_MASTER_KEY_HEX", "").strip()
    if configured:
        return bytes.fromhex(configured)
    return hashlib.sha256(b"sgcc-demo-policy-master-key").digest()


def encrypt_plaintext(plaintext: str) -> Dict[str, str]:
    dek = get_random_bytes(32)
    iv = get_random_bytes(12)
    cipher = AES.new(dek, AES.MODE_GCM, nonce=iv)
    ciphertext, tag = cipher.encrypt_and_digest(plaintext.encode("utf-8"))
    return {
        "dekBase64": _b64encode(dek),
        "ivBase64": _b64encode(iv),
        "encDataBase64": _b64encode(ciphertext + tag),
        "dataHash": _sha256_hex(plaintext.encode("utf-8")),
        "encHash": _sha256_hex(ciphertext + tag),
    }


def wrap_dek(dek_base64: str, policy_expr: str) -> Dict[str, str]:
    nonce = get_random_bytes(12)
    cipher = AES.new(_master_key(), AES.MODE_GCM, nonce=nonce)
    cipher.update(policy_expr.encode("utf-8"))
    ciphertext, tag = cipher.encrypt_and_digest(_b64decode(dek_base64))
    wrapped = nonce + ciphertext + tag
    return {
        "ctAbeMode": "MVP_POLICY_WRAPPED_DEK",
        "wrappedDekBase64": _b64encode(wrapped),
    }


def unwrap_dek(wrapped_dek_base64: str, policy_expr: str) -> bytes:
    wrapped = _b64decode(wrapped_dek_base64)
    nonce = wrapped[:12]
    ciphertext = wrapped[12:-16]
    tag = wrapped[-16:]
    cipher = AES.new(_master_key(), AES.MODE_GCM, nonce=nonce)
    cipher.update(policy_expr.encode("utf-8"))
    return cipher.decrypt_and_verify(ciphertext, tag)


def decrypt_ciphertext(enc_data_base64: str, iv_base64: str, dek: bytes) -> str:
    payload = _b64decode(enc_data_base64)
    ciphertext = payload[:-16]
    tag = payload[-16:]
    cipher = AES.new(dek, AES.MODE_GCM, nonce=_b64decode(iv_base64))
    plaintext = cipher.decrypt_and_verify(ciphertext, tag)
    return plaintext.decode("utf-8")


@dataclass
class CommitmentLeaf:
    key: str
    value: str

    @property
    def leaf_hash(self) -> str:
        # The current MVP still uses a hash-tree style proof, but the field mapping
        # already matches the project document:
        # data_id -> HD_i, Redis stores ProofD_i, blockchain stores Vroot.
        return _sha256_hex(f"{self.key}:{self.value}".encode("utf-8"))


def build_demo_commitment(leaves: List[CommitmentLeaf]) -> Dict[str, Dict[str, object]]:
    if not leaves:
        return {"root": "", "proofs": {}}

    ordered = sorted(leaves, key=lambda item: item.key)
    proofs: Dict[str, Dict[str, object]] = {
        item.key: {
            "leafKey": item.key,
            "hdValue": item.value,
            "leafHash": item.leaf_hash,
            "siblings": [],
        }
        for item in ordered
    }

    current_level = [
        {
            "hash": item.leaf_hash,
            "leafKeys": [item.key],
        }
        for item in ordered
    ]

    while len(current_level) > 1:
        next_level: List[Dict[str, object]] = []

        i = 0
        while i < len(current_level):
            left_node = current_level[i]
            if i + 1 < len(current_level):
                right_node = current_level[i + 1]
            else:
                right_node = current_level[i]

            left_hash = str(left_node["hash"])
            right_hash = str(right_node["hash"])

            for leaf_key in left_node["leafKeys"]:
                proofs[str(leaf_key)]["siblings"].append({"direction": "right", "hash": right_hash})

            if i + 1 < len(current_level):
                for leaf_key in right_node["leafKeys"]:
                    proofs[str(leaf_key)]["siblings"].append({"direction": "left", "hash": left_hash})

            next_level.append(
                {
                    "hash": _sha256_hex(f"{left_hash}{right_hash}".encode("utf-8")),
                    "leafKeys": [*left_node["leafKeys"], *right_node["leafKeys"]]
                    if i + 1 < len(current_level)
                    else [*left_node["leafKeys"]],
                }
            )

            i += 2

        current_level = next_level

    root = str(current_level[0]["hash"])
    for proof in proofs.values():
        proof["pathLength"] = len(proof["siblings"])
        proof["root"] = root

    return {
        "root": root,
        "proofs": proofs,
        "leafHashes": {item.key: item.leaf_hash for item in ordered},
    }


def verify_demo_commitment(
    key: str, value: str, proof: Union[List[Dict[str, str]], Dict[str, object]], root: str
) -> bool:
    cursor = _sha256_hex(f"{key}:{value}".encode("utf-8"))
    proof_steps = proof if isinstance(proof, list) else proof.get("siblings", [])
    for step in proof_steps:
        if step["direction"] == "left":
            cursor = _sha256_hex(f"{step['hash']}{cursor}".encode("utf-8"))
        else:
            cursor = _sha256_hex(f"{cursor}{step['hash']}".encode("utf-8"))
    return cursor == root


class ResourceMetadata(BaseModel):
    dataId: str
    ownerDid: str
    region: str
    dataType: str


class EncryptPlaintextRequest(BaseModel):
    metadata: ResourceMetadata
    plaintext: str
    policyExpr: str


class PackageCiphertextRequest(BaseModel):
    metadata: ResourceMetadata
    encDataBase64: str
    ivBase64: str
    dekBase64: str
    policyExpr: str
    dataHash: Optional[str] = None


class CommitmentItem(BaseModel):
    key: str = Field(description="Usually data_id in the current MVP.")
    value: str = Field(description="Usually HD_i = H(Package).")


class CommitmentRequest(BaseModel):
    items: List[CommitmentItem]


class VerifyRequest(BaseModel):
    key: str
    value: str
    proof: Union[List[Dict[str, str]], Dict[str, object]]
    root: str


class DecryptRequest(BaseModel):
    encDataBase64: str
    ivBase64: str
    wrappedDekBase64: str
    policyExpr: str


def package_ciphertext(request: PackageCiphertextRequest) -> dict:
    wrapped = wrap_dek(request.dekBase64, request.policyExpr)
    package = {
        "dataId": request.metadata.dataId,
        "ownerDid": request.metadata.ownerDid,
        "region": request.metadata.region,
        "dataType": request.metadata.dataType,
        "policyExpr": request.policyExpr,
        "policyHash": _sha256_hex(request.policyExpr.encode("utf-8")),
        "cipher": {
            "algorithm": "AES/GCM",
            "ivBase64": request.ivBase64,
            "encDataBase64": request.encDataBase64,
            "encHash": _sha256_hex(_b64decode(request.encDataBase64)),
        },
        "keyEnvelope": {
            "ctAbeMode": wrapped["ctAbeMode"],
            "wrappedDekBase64": wrapped["wrappedDekBase64"],
        },
        "hashes": {
            "dataHash": request.dataHash or "",
        },
    }
    package_json = _canonical_json(package)
    return {
        "package": package,
        "packageJson": package_json,
        "packageHash": _sha256_hex(package_json.encode("utf-8")),
        "policyHash": package["policyHash"],
        "encHash": package["cipher"]["encHash"],
        "dataHash": package["hashes"]["dataHash"],
        "wrappedDekBase64": wrapped["wrappedDekBase64"],
    }


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/privacy/encrypt-plaintext")
def encrypt_plaintext_endpoint(request: EncryptPlaintextRequest) -> dict:
    encrypted = encrypt_plaintext(request.plaintext)
    packaged = package_ciphertext(
        PackageCiphertextRequest(
            metadata=request.metadata,
            encDataBase64=encrypted["encDataBase64"],
            ivBase64=encrypted["ivBase64"],
            dekBase64=encrypted["dekBase64"],
            policyExpr=request.policyExpr,
            dataHash=encrypted["dataHash"],
        )
    )
    return {**encrypted, **packaged}


@app.post("/api/privacy/package-ciphertext")
def package_ciphertext_endpoint(request: PackageCiphertextRequest) -> dict:
    return package_ciphertext(request)


@app.post("/api/privacy/commitments")
def commitments_endpoint(request: CommitmentRequest) -> dict:
    leaves = [CommitmentLeaf(key=item.key, value=item.value) for item in request.items]
    return build_demo_commitment(leaves)


@app.post("/api/privacy/verify")
def verify_endpoint(request: VerifyRequest) -> dict:
    return {
        "verified": verify_demo_commitment(
            key=request.key,
            value=request.value,
            proof=request.proof,
            root=request.root,
        )
    }


@app.post("/api/privacy/decrypt")
def decrypt_endpoint(request: DecryptRequest) -> dict:
    dek = unwrap_dek(request.wrappedDekBase64, request.policyExpr)
    plaintext = decrypt_ciphertext(request.encDataBase64, request.ivBase64, dek)
    return {"plaintext": plaintext}
