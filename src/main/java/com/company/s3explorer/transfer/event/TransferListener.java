package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

import java.util.List;

public interface TransferListener {

    void onTransferUpdated(
            TransferRuntime runtime);

    default void onQueuedTransfersCancelled(
            List<TransferRuntime> runtimes) {
    }

    default void onProducerUpdated(
            ProducerRuntime runtime) {
    }

    default void onTransferGroupUpdated(
            TransferGroup group) {
    }

    default void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {
    }
}