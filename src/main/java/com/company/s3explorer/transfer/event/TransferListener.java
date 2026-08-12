package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

public interface TransferListener {

    void onTransferUpdated(TransferRuntime runtime);

    default void onProducerUpdated(ProducerRuntime runtime) {
    }
}