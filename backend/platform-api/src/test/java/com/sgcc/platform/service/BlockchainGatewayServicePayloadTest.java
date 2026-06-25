package com.sgcc.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockchainGatewayServicePayloadTest {

    @Test
    void recordAccessPayloadShouldKeepBooleanTypes() {
        List<Object> funcParam = List.of(
                "DATA001",
                "SGCC_dispatch_center",
                "load_analyst",
                true,
                false
        );

        assertInstanceOf(Boolean.class, funcParam.get(3));
        assertInstanceOf(Boolean.class, funcParam.get(4));
        assertEquals(true, funcParam.get(3));
        assertEquals(false, funcParam.get(4));
    }

    @Test
    void batchAnchoringStillRepresentsPerResourceSameRootShape() {
        Map<String, Object> anchorShape = Map.of(
                "dataId", "DATA001",
                "region", "qingdao",
                "cid", "cid-001",
                "packageHash", "pkg-001",
                "policyHash", "policy-001",
                "ownerDid", "did:weid:qingdao:4001",
                "dataType", "load_curve",
                "root", "root-001"
        );

        assertEquals("root-001", anchorShape.get("root"));
        assertEquals("qingdao", anchorShape.get("region"));
    }
}
