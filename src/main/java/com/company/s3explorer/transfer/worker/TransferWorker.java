package com.company.s3explorer.transfer.worker;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.operation.TransferOperation;
import com.company.s3explorer.transfer.queue.TransferQueue;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public class TransferWorker implements Runnable {

    private final TransferQueue queue;
    private final TransferContext context;
    private final TransferOperationFactory operationFactory;
    private final BooleanSupplier shouldRetire;
    private final Runnable workerFinished;

    public TransferWorker(
            TransferQueue queue,
            TransferContext context,
            TransferOperationFactory operationFactory,
            BooleanSupplier shouldRetire,
            Runnable workerFinished) {

        this.queue = queue;
        this.context = context;
        this.operationFactory = operationFactory;
        this.shouldRetire = shouldRetire;
        this.workerFinished = workerFinished;
    }

    @Override
    public void run() {

        try {

            while (!Thread.currentThread().isInterrupted()) {

                if (shouldRetire.getAsBoolean()) {
                    return;
                }

                TransferRuntime runtime;

                try {

                    runtime =
                            queue.poll(500);

                }
                catch (InterruptedException ex) {
                    return;
                }

                if (runtime == null) {
                    continue;
                }

                queue.markActive(runtime);

                try {
                    process(runtime);
                }
                finally {
                    queue.markFinished(runtime);
                }
            }

        }
        finally {

            workerFinished.run();
        }
    }

    private void process(
            TransferRuntime runtime) {

        try {

            TransferOperation operation =
                    operationFactory.get(
                            runtime.getTask().getType());

            operation.execute(
                    runtime,
                    context);

        }
        catch (CancellationException ex) {

            context.publishCancelled(runtime);

        }
        catch (Exception ex) {

            ex.printStackTrace();

            context.publishFailed(
                    runtime,
                    ex);
        }
    }
}