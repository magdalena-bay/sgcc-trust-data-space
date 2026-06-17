package com.sgcc.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccessResponse {
    private boolean granted;
    private boolean verified;
    private String message;
    private String plaintext;
    private String hdValue;
    private String packageHash;
    private String cid;
    private String root;
}
