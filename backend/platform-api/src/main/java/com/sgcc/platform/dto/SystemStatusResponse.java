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
    private Map<String, Boolean> storageStatus;
    private Map<String, Boolean> chainStatus;
    private Map<String, String> contractRegistry;
    private Map<String, Long> contractRegistryCount;
    private boolean crossChainContractAddressReuseDetected;
}
