package com.sgcc.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceVerkleResponse {
    private String dataId;
    private String hdValue;
    private String proofKey;
    private String proofJson;
    private String regionRoot;
    private String relayRoot;
    private String chainRoot;
    private boolean chainAnchorExists;
}
