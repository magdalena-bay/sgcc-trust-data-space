package com.sgcc.platform.onchain;

public interface OnChainWriteDispatcher {

    void dispatch(OnChainWriteCommand command);
}
