package com.company.s3explorer.transfer.worker;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.queue.TransferQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public WorkerExecutor(
            int threadCount,
            TransferQueue queue,
            TransferContext context,
            TransferOperationFactory factory) {

        executor = Executors.newFixedThreadPool(
                threadCount,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("TransferWorker");
                    t.setDaemon(true);
                    return t;
                });

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    new TransferWorker(
                            queue,
                            context,
                            factory));
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
