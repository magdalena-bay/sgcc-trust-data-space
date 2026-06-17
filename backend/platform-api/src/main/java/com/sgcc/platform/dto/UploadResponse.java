package com.sgcc.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponse {
    private String dataId;
    private String region;
    private String cid;
    private String hdValue;
    private String packageHash;
    private String root;
    private String relayRoot;
    private String message;
}
