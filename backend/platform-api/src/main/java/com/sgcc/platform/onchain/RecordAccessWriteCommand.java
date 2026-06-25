package com.sgcc.platform.onchain;

public record RecordAccessWriteCommand(
        String dataId,
        String requesterOrg,
        String requesterRole,
        boolean verified,
        boolean granted
) implements OnChainWriteCommand {

    @Override
    public OnChainWriteType type() {
        return OnChainWriteType.RECORD_ACCESS;
    }
}
