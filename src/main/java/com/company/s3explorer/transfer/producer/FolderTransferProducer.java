package com.company.s3explorer.transfer.producer;

import java.io.IOException;

public interface FolderTransferProducer {
    void produce() throws IOException;
}
