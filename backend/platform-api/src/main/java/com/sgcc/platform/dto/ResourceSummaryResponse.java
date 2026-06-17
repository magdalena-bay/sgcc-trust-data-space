package com.sgcc.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceSummaryResponse {
    private String dataId;
    private String region;
    private String ownerDid;
    private String dataType;
    private String cid;
    private String hdValue;
    private String packageHash;
    private String root;
    private String status;
}
