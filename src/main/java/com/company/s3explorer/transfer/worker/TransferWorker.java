package com.company.s3explorer.transfer.worker;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.operation.TransferOperation;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.queue.TransferQueue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

public class TransferWorker implements Runnable {
    private final TransferQueue queue;
    private final TransferContext context;
    private final TransferOperationFactory operationFactory;

    public TransferWorker(
            TransferQueue queue,
            TransferContext context,
            TransferOperationFactory operationFactory) {

        this.queue = queue;
        this.context = context;
        this.operationFactory = operationFactory;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TransferRuntime runtime = queue.take();
                queue.markActive(runtime);

                try {
                    process(runtime);
                } finally {
                    queue.markFinished(runtime);
                }
            } catch (InterruptedException ex) {
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void process(TransferRuntime runtime) {
        try {
            TransferOperation operation = operationFactory.get(runtime.getTask().getType());
            operation.execute(runtime, context);
        } catch (CancellationException ex) {
            //
        } catch (Exception ex) {
            ex.printStackTrace();
            context.publishFailed(runtime, ex);
        }
    }
}