// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract SgccTrustAnchor {
    struct ResourceAnchor {
        string dataId;
        string region;
        string cid;
        string packageHash;
        string policyHash;
        string ownerDid;
        string dataType;
        string vroot;
        uint256 createdAt;
        bool exists;
    }

    mapping(string => ResourceAnchor) private anchors;

    event ResourceAnchored(
        string indexed dataId,
        string indexed region,
        string cid,
        string packageHash,
        string policyHash,
        string ownerDid,
        string dataType,
        string vroot,
        uint256 createdAt
    );

    event AccessRecorded(
        string indexed dataId,
        string requesterOrg,
        string requesterRole,
        bool verified,
        bool granted,
        uint256 accessAt
    );

    function anchorResource(
        string memory dataId,
        string memory region,
        string memory cid,
        string memory packageHash,
        string memory policyHash,
        string memory ownerDid,
        string memory dataType,
        string memory vroot
    ) public {
        anchors[dataId] = ResourceAnchor(
            dataId,
            region,
            cid,
            packageHash,
            policyHash,
            ownerDid,
            dataType,
            vroot,
            block.timestamp,
            true
        );

        emit ResourceAnchored(
            dataId,
            region,
            cid,
            packageHash,
            policyHash,
            ownerDid,
            dataType,
            vroot,
            block.timestamp
        );
    }

    function recordAccess(
        string memory dataId,
        string memory requesterOrg,
        string memory requesterRole,
        bool verified,
        bool granted
    ) public {
        emit AccessRecorded(dataId, requesterOrg, requesterRole, verified, granted, block.timestamp);
    }

    function getAnchor(string memory dataId)
        public
        view
        returns (
            string memory region,
            string memory cid,
            string memory packageHash,
            string memory policyHash,
            string memory ownerDid,
            string memory dataType,
            string memory vroot,
            uint256 createdAt,
            bool exists
        )
    {
        ResourceAnchor memory anchor = anchors[dataId];
        return (
            anchor.region,
            anchor.cid,
            anchor.packageHash,
            anchor.policyHash,
            anchor.ownerDid,
            anchor.dataType,
            anchor.vroot,
            anchor.createdAt,
            anchor.exists
        );
    }
}
