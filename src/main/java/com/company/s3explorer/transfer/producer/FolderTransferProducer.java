package com.company.s3explorer.transfer.producer;

import java.io.IOException;

public interface FolderTransferProducer {

    String getDescription();

    void produce(ProducerRuntime runtime) throws IOException;
}