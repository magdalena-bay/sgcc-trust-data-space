package com.sgcc.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccessRequest {
    @NotBlank
    private String dataId;
    @NotBlank
    private String requesterOrg;
    @NotBlank
    private String requesterRole;
    @NotBlank
    private String requesterGrantStatus;
}
