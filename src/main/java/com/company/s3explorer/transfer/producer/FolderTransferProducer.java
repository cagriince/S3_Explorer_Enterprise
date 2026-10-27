package com.company.s3explorer.transfer.producer;

import java.io.IOException;

public interface FolderTransferProducer {

    void produce(
            ProducerRuntime runtime)
            throws IOException;

    default void cancelBeforeStart() {
    }
}