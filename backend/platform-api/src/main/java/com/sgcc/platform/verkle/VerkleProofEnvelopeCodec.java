package com.sgcc.platform.verkle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerkleProofEnvelopeCodec {

    public static final String DEMO_SCHEME = "verkle-compatible-demo";
    public static final String DEMO_ENGINE_VERSION = "hash-tree-v1";
    public static final String DEFAULT_PROOF_TYPE = "membership";

    private final ObjectMapper objectMapper;

    public StoredProofEnvelope fromCommitment(CommitmentResult commitmentResult, String leafKey, String valueDigest) {
        return StoredProofEnvelope.builder()
                .scheme(normalizeScheme(commitmentResult.getScheme()))
                .engineVersion(normalizeEngineVersion(commitmentResult.getEngineVersion()))
                .proofType(DEFAULT_PROOF_TYPE)
                .leafKey(leafKey)
                .valueDigest(valueDigest)
                .root(commitmentResult.getRoot())
                .proofPayload(commitmentResult.proofFor(leafKey))
                .build();
    }

    public StoredProofEnvelope read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (parsed.containsKey("proofPayload")) {
                StoredProofEnvelope envelope = objectMapper.convertValue(parsed, StoredProofEnvelope.class);
                envelope.setScheme(normalizeScheme(envelope.getScheme()));
                envelope.setEngineVersion(normalizeEngineVersion(envelope.getEngineVersion()));
                envelope.setProofType(normalizeProofType(envelope.getProofType()));
                return envelope;
            }
            return legacyEnvelope(parsed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse redis proof envelope", ex);
        }
    }

    public String write(StoredProofEnvelope envelope) {
        if (envelope == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize proof envelope", ex);
        }
    }

    public boolean equivalent(StoredProofEnvelope left, StoredProofEnvelope right) {
        if (left == null || right == null) {
            return false;
        }
        return safeEquals(normalizeScheme(left.getScheme()), normalizeScheme(right.getScheme()))
                && safeEquals(normalizeEngineVersion(left.getEngineVersion()), normalizeEngineVersion(right.getEngineVersion()))
                && safeEquals(normalizeProofType(left.getProofType()), normalizeProofType(right.getProofType()))
                && safeEquals(left.getLeafKey(), right.getLeafKey())
                && safeEquals(left.getValueDigest(), right.getValueDigest())
                && safeEquals(left.getRoot(), right.getRoot())
                && jsonEquivalent(left.getProofPayload(), right.getProofPayload());
    }

    public String normalizeScheme(String scheme) {
        return scheme == null || scheme.isBlank() ? DEMO_SCHEME : scheme;
    }

    public String normalizeEngineVersion(String engineVersion) {
        return engineVersion == null || engineVersion.isBlank() ? DEMO_ENGINE_VERSION : engineVersion;
    }

    public String normalizeProofType(String proofType) {
        return proofType == null || proofType.isBlank() ? DEFAULT_PROOF_TYPE : proofType;
    }

    private StoredProofEnvelope legacyEnvelope(Map<String, Object> legacyProof) {
        return StoredProofEnvelope.builder()
                .scheme(DEMO_SCHEME)
                .engineVersion(DEMO_ENGINE_VERSION)
                .proofType(DEFAULT_PROOF_TYPE)
                .leafKey(stringValue(legacyProof.get("leafKey")))
                .valueDigest(stringValue(legacyProof.get("hdValue")))
                .root(stringValue(legacyProof.get("root")))
                .proofPayload(legacyProof)
                .build();
    }

    private boolean jsonEquivalent(Object left, Object right) {
        JsonNode leftTree = objectMapper.valueToTree(left);
        JsonNode rightTree = objectMapper.valueToTree(right);
        return leftTree.equals(rightTree);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean safeEquals(String left, String right) {
        return left != null && left.equals(right);
    }
}
