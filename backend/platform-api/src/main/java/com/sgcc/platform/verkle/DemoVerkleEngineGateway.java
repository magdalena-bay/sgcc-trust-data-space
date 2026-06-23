package com.sgcc.platform.verkle;

import com.sgcc.platform.service.PrivacyClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoVerkleEngineGateway implements VerkleEngineGateway {

    private final PrivacyClient privacyClient;
    private final VerkleProofEnvelopeCodec proofEnvelopeCodec;

    @Override
    public String scheme() {
        return VerkleProofEnvelopeCodec.DEMO_SCHEME;
    }

    @Override
    public String engineVersion() {
        return VerkleProofEnvelopeCodec.DEMO_ENGINE_VERSION;
    }

    @Override
    public CommitmentResult commitments(List<Map<String, String>> items) {
        return privacyClient.commitments(items);
    }

    @Override
    public StoredProofEnvelope buildEnvelope(CommitmentResult commitmentResult, String leafKey, String valueDigest) {
        return proofEnvelopeCodec.fromCommitment(commitmentResult, leafKey, valueDigest);
    }

    @Override
    public StoredProofEnvelope readEnvelope(String json) {
        return proofEnvelopeCodec.read(json);
    }

    @Override
    public String writeEnvelope(StoredProofEnvelope envelope) {
        return proofEnvelopeCodec.write(envelope);
    }

    @Override
    public boolean equivalent(StoredProofEnvelope left, StoredProofEnvelope right) {
        return proofEnvelopeCodec.equivalent(left, right);
    }

    @Override
    public boolean verify(String key, String valueDigest, StoredProofEnvelope proofEnvelope, String root) {
        return privacyClient.verify(key, valueDigest, proofEnvelope, root);
    }
}
