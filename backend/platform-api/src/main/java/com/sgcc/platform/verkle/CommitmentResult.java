package com.sgcc.platform.verkle;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitmentResult {
    private String scheme;
    private String engineVersion;
    private String root;
    private Map<String, Object> proofByKey;
    private Map<String, String> leafHashes;

    public Object proofFor(String key) {
        if (proofByKey == null) {
            return null;
        }
        return proofByKey.get(key);
    }
}
