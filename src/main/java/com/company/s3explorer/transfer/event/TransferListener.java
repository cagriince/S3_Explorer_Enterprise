package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;

public interface TransferListener {
    void onTransferUpdated(TransferRuntime runtime);
}
