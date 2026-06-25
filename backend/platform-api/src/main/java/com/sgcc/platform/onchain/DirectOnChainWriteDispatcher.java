package com.sgcc.platform.onchain;

import com.sgcc.platform.service.BlockchainGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectOnChainWriteDispatcher implements OnChainWriteDispatcher {

    private final BlockchainGatewayService blockchainGatewayService;

    @Override
    public void dispatch(OnChainWriteCommand command) {
        switch (command.type()) {
            case ANCHOR_RESOURCE -> {
                AnchorResourceWriteCommand anchor = (AnchorResourceWriteCommand) command;
                blockchainGatewayService.anchorResource(anchor.chainName(), anchor.resource(), anchor.root());
            }
            case RECORD_ACCESS -> {
                RecordAccessWriteCommand access = (RecordAccessWriteCommand) command;
                blockchainGatewayService.recordAccess(
                        access.dataId(),
                        access.requesterOrg(),
                        access.requesterRole(),
                        access.verified(),
                        access.granted()
                );
            }
            default -> throw new IllegalArgumentException("unsupported on-chain write command: " + command.type());
        }
    }
}
