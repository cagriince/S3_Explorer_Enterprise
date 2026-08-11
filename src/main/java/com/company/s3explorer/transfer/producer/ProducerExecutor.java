package com.company.s3explorer.transfer.producer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProducerExecutor implements AutoCloseable {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FolderTransferProducer");
                t.setDaemon(true);
                return t;
            });

    public void submit(FolderTransferProducer producer) {
        executor.submit(() -> {
            try {
                producer.produce();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
