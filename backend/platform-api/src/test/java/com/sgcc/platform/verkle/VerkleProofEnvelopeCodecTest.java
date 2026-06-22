package com.sgcc.platform.verkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerkleProofEnvelopeCodecTest {

    private final VerkleProofEnvelopeCodec codec = new VerkleProofEnvelopeCodec(new ObjectMapper());

    @Test
    void readsLegacyProofAsNormalizedEnvelope() {
        String legacyJson = """
                {
                  "leafKey":"DTEST001",
                  "hdValue":"hd-demo-001",
                  "leafHash":"leaf-hash",
                  "siblings":[{"direction":"right","hash":"abc"}],
                  "pathLength":1,
                  "root":"root-demo-001"
                }
                """;

        StoredProofEnvelope envelope = codec.read(legacyJson);

        assertEquals(VerkleProofEnvelopeCodec.DEMO_SCHEME, envelope.getScheme());
        assertEquals(VerkleProofEnvelopeCodec.DEMO_ENGINE_VERSION, envelope.getEngineVersion());
        assertEquals("membership", envelope.getProofType());
        assertEquals("DTEST001", envelope.getLeafKey());
        assertEquals("hd-demo-001", envelope.getValueDigest());
        assertEquals("root-demo-001", envelope.getRoot());
    }

    @Test
    void comparesLegacyAndEnvelopeProofAsEquivalent() {
        StoredProofEnvelope rebuiltEnvelope = StoredProofEnvelope.builder()
                .scheme(VerkleProofEnvelopeCodec.DEMO_SCHEME)
                .engineVersion(VerkleProofEnvelopeCodec.DEMO_ENGINE_VERSION)
                .proofType("membership")
                .leafKey("DTEST001")
                .valueDigest("hd-demo-001")
                .root("root-demo-001")
                .proofPayload(Map.of(
                        "leafKey", "DTEST001",
                        "hdValue", "hd-demo-001",
                        "leafHash", "leaf-hash",
                        "siblings", java.util.List.of(Map.of("direction", "right", "hash", "abc")),
                        "pathLength", 1,
                        "root", "root-demo-001"
                ))
                .build();

        StoredProofEnvelope legacyEnvelope = codec.read("""
                {
                  "leafKey":"DTEST001",
                  "hdValue":"hd-demo-001",
                  "leafHash":"leaf-hash",
                  "siblings":[{"direction":"right","hash":"abc"}],
                  "pathLength":1,
                  "root":"root-demo-001"
                }
                """);

        assertTrue(codec.equivalent(legacyEnvelope, rebuiltEnvelope));
        assertEquals(codec.write(rebuiltEnvelope), codec.write(codec.read(codec.write(rebuiltEnvelope))));
    }
}
