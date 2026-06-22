package com.sgcc.platform.verkle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredProofEnvelope {
    private String scheme;
    private String engineVersion;
    private String proofType;
    private String leafKey;
    private String valueDigest;
    private String root;
    private Object proofPayload;
}
