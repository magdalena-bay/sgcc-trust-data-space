package com.sgcc.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadRequest {
    private String dataId;
    @NotBlank
    private String region;
    @NotBlank
    private String ownerDid;
    @NotBlank
    private String dataType;
    @NotBlank
    private String policyOrg;
    @NotBlank
    private String policyRole;
    @NotBlank
    private String policyGrantStatus;
    private String plaintext;
    private String encDataBase64;
    private String ivBase64;
    private String dekBase64;
    private String dataHash;

    public boolean hasCiphertextPayload() {
        return encDataBase64 != null && !encDataBase64.isBlank()
                && ivBase64 != null && !ivBase64.isBlank()
                && dekBase64 != null && !dekBase64.isBlank();
    }

    public String policyExpr() {
        return "org=" + policyOrg + ";role=" + policyRole + ";grantStatus=" + policyGrantStatus;
    }
}
