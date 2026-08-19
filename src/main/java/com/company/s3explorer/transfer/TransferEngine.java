package com.company.s3explorer.transfer;

import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.producer.ProducerExecutor;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.transfer.worker.WorkerExecutor;

public class TransferEngine
        implements AutoCloseable {

    private final TransferEventBus eventBus;
    private final TransferQueue queue;
    private final ProducerExecutor producerExecutor;
    private final WorkerExecutor workerExecutor;
    private final TransferManager transferManager;

    public TransferEngine(
            S3ClientManager clientManager,
            int threadCount) {

        eventBus =
                new TransferEventBus();

        queue =
                new TransferQueue(
                        eventBus);

        producerExecutor =
                new ProducerExecutor(
                        eventBus);

        TransferContext context =
                new TransferContext(
                        clientManager,
                        eventBus);

        workerExecutor =
                new WorkerExecutor(
                        threadCount,
                        queue,
                        context,
                        new TransferOperationFactory());

        transferManager =
                new TransferManager(
                        clientManager,
                        queue,
                        eventBus,
                        producerExecutor);
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }

    public TransferEventBus getEventBus() {
        return eventBus;
    }

    public void setThreadCount(
            int threadCount) {

        workerExecutor.setThreadCount(
                threadCount);
    }

    public int getThreadCount() {

        return workerExecutor.getThreadCount();
    }

    public int getActiveWorkerCount() {

        return workerExecutor
                .getActiveWorkerCount();
    }

    @Override
    public void close() {

        producerExecutor.close();

        workerExecutor.close();
    }
}