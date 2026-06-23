package com.sgcc.platform.verkle;

import java.util.List;
import java.util.Map;

public interface VerkleEngineGateway {

    String scheme();

    String engineVersion();

    CommitmentResult commitments(List<Map<String, String>> items);

    StoredProofEnvelope buildEnvelope(CommitmentResult commitmentResult, String leafKey, String valueDigest);

    StoredProofEnvelope readEnvelope(String json);

    String writeEnvelope(StoredProofEnvelope envelope);

    boolean equivalent(StoredProofEnvelope left, StoredProofEnvelope right);

    boolean verify(String key, String valueDigest, StoredProofEnvelope proofEnvelope, String root);
}
