package com.sgcc.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceVerkleAuditResponse {
    private String dataId;
    private String region;
    private String cid;
    private String hdValue;
    private String redisProofKey;
    private String mysqlPackageHash;
    private String ipfsPackageHash;
    private String mysqlPolicyHash;
    private String ipfsPolicyHash;
    private String mysqlRoot;
    private String rebuiltRoot;
    private String regionChainRoot;
    private String mysqlRelayRoot;
    private String relayChainRoot;
    private boolean redisProofExists;
    private boolean regionChainAnchorExists;
    private boolean relayChainAnchorExists;
    private boolean mysqlHdMatchesPackageHash;
    private boolean mysqlPackageHashMatchesIpfsHash;
    private boolean mysqlPolicyHashMatchesIpfsPolicyHash;
    private boolean redisProofMatchesRebuilt;
    private boolean rebuiltRootMatchesMysqlRoot;
    private boolean rebuiltRootMatchesRegionChainRoot;
    private boolean mysqlRelayRootMatchesRelayChainRoot;
    private boolean proofVerifiesAgainstMysqlRoot;
    private boolean proofVerifiesAgainstRegionChainRoot;
    private boolean proofVerifiesAgainstRelayChainRoot;
    private boolean overallPassed;
    private String redisProofJson;
    private String rebuiltProofJson;
}
