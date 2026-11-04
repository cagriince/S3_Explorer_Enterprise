
package com.company.s3explorer.transfer;

import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.logging.TransferLogListener;
import com.company.s3explorer.transfer.logging.TransferLogWriter;
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

    private final TransferLogWriter logWriter;

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
                        context,
                        queue,
                        producerExecutor);

        logWriter =
                new TransferLogWriter();

        eventBus.subscribe(
                new TransferLogListener(logWriter));
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }

    public TransferEventBus getEventBus() {
        return eventBus;
    }

    public TransferLogWriter getLogWriter() {
        return logWriter;
    }

    public void setThreadCount(
            int threadCount) {

        workerExecutor.setThreadCount(
                threadCount);
    }

    @Override
    public void close() {

        transferManager.close();
        producerExecutor.close();
        workerExecutor.close();

        /*
         * Writer en son kapatılır.
         *
         * Böylece producer ve worker tarafındaki
         * son transfer olaylarının log kuyruğuna
         * ulaşması için fırsat verilir.
         */
        logWriter.close();
    }
}
