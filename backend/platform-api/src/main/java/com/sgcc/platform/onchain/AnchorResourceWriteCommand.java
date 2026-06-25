package com.sgcc.platform.onchain;

import com.sgcc.platform.entity.DataResourceEntity;

public record AnchorResourceWriteCommand(
        String chainName,
        DataResourceEntity resource,
        String root
) implements OnChainWriteCommand {

    @Override
    public OnChainWriteType type() {
        return OnChainWriteType.ANCHOR_RESOURCE;
    }
}
