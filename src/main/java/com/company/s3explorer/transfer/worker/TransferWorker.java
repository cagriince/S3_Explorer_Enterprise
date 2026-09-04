package com.company.s3explorer.transfer.worker;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.model.TransferGroup;
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

            while (!Thread.currentThread()
                    .isInterrupted()) {

                /*
                 * Dinamik thread count azaltıldıysa
                 * worker emekli olabilir.
                 */
                if (shouldRetire.getAsBoolean()) {
                    return;
                }

                /*
                 * Cancel All sırasında yeni iş alma.
                 *
                 * Worker burada bekler; thread ölmez.
                 */
                if (queue.isCancellingAll()) {

                    try {
                        Thread.sleep(50);
                    }
                    catch (InterruptedException ex) {

                        Thread.currentThread()
                                .interrupt();

                        return;
                    }

                    continue;
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

                /*
                 * Cancel All poll ile aynı anda yarıştıysa
                 * queue.poll() runtime'ı cancelled yapıp
                 * null döndürecektir.
                 */
                if (queue.isCancellingAll()) {
                    continue;
                }

                queue.markActive(runtime);

                try {

                    process(runtime);

                }
                finally {

                    queue.markFinished(
                            runtime);
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

            /*
             * Cancel All sırasında task worker'a
             * ulaşmışsa operation başlamadan önce
             * kontrol et.
             */
            if (runtime.isCancelRequested()) {

                TransferGroup group =
                        runtime.getTask().getGroup();

                if (group != null) {
                    group.cancelledFromQueue();
                }

                context.publishCancelled(
                        runtime);

                return;
            }

            TransferOperation operation =
                    operationFactory.get(
                            runtime.getTask()
                                    .getType());

            operation.execute(
                    runtime,
                    context);

        }
        catch (CancellationException ex) {

            /*
             * AbstractTransferOperation cancellation'ı
             * zaten group seviyesinde işledi.
             *
             * Burada tekrar cancelled++ yapma.
             */
            context.publishCancelled(
                    runtime);
        }
        catch (Exception ex) {

            if (runtime.isCancelRequested()
                    || Thread.currentThread().isInterrupted()) {

                /*
                 * AbstractTransferOperation cancellation'ı
                 * zaten group'a işlemiştir.
                 *
                 * Burada ikinci kez cancelled++ yapma.
                 */
                context.publishCancelled(
                        runtime);

            }
            else {

                ex.printStackTrace();

                context.publishFailed(
                        runtime,
                        ex);
            }
        }
    }
}