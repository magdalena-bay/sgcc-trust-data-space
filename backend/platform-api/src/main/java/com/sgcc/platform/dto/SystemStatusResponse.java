package com.sgcc.platform.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemStatusResponse {
    private String platformApi;
    private String privacyServiceBaseUrl;
    private String ipfsGatewayUrl;
    private String qingdaoWebaseUrl;
    private String weifangWebaseUrl;
    private String relayWebaseUrl;
    private Map<String, Boolean> components;
}
