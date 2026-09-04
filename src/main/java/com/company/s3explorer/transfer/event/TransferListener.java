package com.company.s3explorer.transfer.event;

import java.util.List;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

public interface TransferListener {

    void onTransferUpdated(
            TransferRuntime runtime);

    default void onTransfersUpdated(
            List<TransferRuntime> runtimes) {

        for (TransferRuntime runtime :
                runtimes) {

            onTransferUpdated(runtime);
        }
    }

    default void onProducerUpdated(
            ProducerRuntime runtime) {
    }

    default void onTransferGroupUpdated(
            TransferGroupUpdatedEvent event) {
    }

    default void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {
    }
}