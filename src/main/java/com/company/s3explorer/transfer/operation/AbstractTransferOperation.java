package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.service.TransferProgressListener;
import com.company.s3explorer.transfer.model.TransferGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CancellationException;

public abstract class AbstractTransferOperation
        implements TransferOperation {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AbstractTransferOperation.class);

    @Override
    public final void execute(
            TransferRuntime runtime,
            TransferContext transferContext)
            throws Exception {

        TransferGroup group =
                runtime.getTask().getGroup();

        if (group != null) {

            group.running();

            publishGroupUpdated(
                    group,
                    transferContext);
        }

        transferContext.publishRunning(
                runtime);

        try {

            checkCancelled(runtime);

            doExecute(
                    runtime,
                    transferContext);

            checkCancelled(runtime);

            log.debug(
                    "[OPERATION BEFORE COMPLETE] {} key={}",
                    runtime.getTask().getId(),
                    runtime.getTask().getObjectKey());

            /*
             * Önce task'ın COMPLETED event'ini yayınla.
             *
             * ExplorerPanel bu event sırasında task'ı
             * completedGroupTasks collector'ına ekliyor.
             *
             * Group completion bundan sonra tetiklenmelidir.
             *
             * Aksi halde son task için:
             *
             *     group.completed()
             *          ↓
             *     group callback
             *          ↓
             *     Explorer onTransferGroupCompleted()
             *          ↓
             *     collector okunuyor
             *          ↓
             *     son task henüz collector'da yok
             *
             * yarışı oluşur.
             */
            transferContext.publishCompleted(
                    runtime);

            log.debug(
                    "[OPERATION PUBLISHED COMPLETED] {}",
                    runtime.getTask().getId());

            /*
             * Artık Explorer task'ı collector'a almış durumda.
             *
             * Bundan sonra group.completed() çağrılır.
             *
             * Son task ise group completion callback'i
             * artık bütün completed task'lar collector'a
             * girdikten sonra çalışacaktır.
             */
            if (group != null) {

                group.completed();

                publishGroupUpdated(
                        group,
                        transferContext);
            }
        }
        catch (CancellationException ex) {

            if (group != null) {

                group.cancelledFromRunning();

                publishGroupUpdated(
                        group,
                        transferContext);
            }

            transferContext.publishCancelled(
                    runtime);

        }
        catch (Exception ex) {

            /*
             * Operation sırasında cancellation request geldiyse
             * bunu normal transfer failure olarak sayma.
             *
             * Böylece:
             *
             * running--
             * cancelled++
             *
             * yapılır ve failed++ yapılmaz.
             */
            if (runtime.isCancelRequested()
                    || Thread.currentThread().isInterrupted()) {

                if (group != null) {

                    group.cancelledFromRunning();

                    publishGroupUpdated(
                            group,
                            transferContext);
                }

                transferContext.publishCancelled(
                        runtime);

                return;
            }

            if (group != null) {

                group.failed(
                        runtime.getTask());

                publishGroupUpdated(
                        group,
                        transferContext);
            }

            transferContext.publishFailed(
                    runtime,
                    ex);

            throw ex;
        }
    }

    private void publishGroupUpdated(
            TransferGroup group,
            TransferContext transferContext) {

        String repository =
                group.getSourceRepository();

        String bucket =
                group.getSourceBucket();

        String prefix =
                group.getSourcePrefix();

        /*
         * Eski/generic single-object gruplar için
         * geriye dönük uyumluluk.
         */
        if (repository == null
                || bucket == null
                || prefix == null) {

            return;
        }

        transferContext.publishGroupUpdated(
                group,
                repository,
                bucket,
                prefix,
                group.getOperation() == TransferType.MOVE || group.getOperation() == TransferType.MOVE_GROUP || group.getOperation() == TransferType.RENAME || group.getOperation() == TransferType.RENAME_GROUP);
    }
    
    protected void checkCancelled(
            TransferRuntime runtime)
            throws CancellationException {

        if (runtime.isCancelRequested()) {
            throw new CancellationException();
        }
    }

    protected TransferProgressListener
    createProgressListener(
            TransferRuntime runtime,
            TransferContext transferContext) {

        return (transferred, total) -> {

            checkCancelled(runtime);

            runtime.updateProgress(
                    transferred,
                    total);

            if (runtime.shouldPublishUi(100)) {

                transferContext.publishProgress(
                        runtime);
            }
        };
    }

    protected void updateProgressCompleted(
            TransferRuntime runtime,
            TransferContext transferContext) {

        runtime.progressCompleted();

        runtime.forceNextUiPublish();

        transferContext.publishProgress(
                runtime);
    }

    protected void updateProgressPercent(
            TransferRuntime runtime,
            TransferContext transferContext,
            int percent) {

        checkCancelled(runtime);

        runtime.updateProgress(
                percent,
                100);

        if (runtime.shouldPublishUi(100)) {

            transferContext.publishProgress(
                    runtime);
        }
    }

    protected abstract void doExecute(
            TransferRuntime runtime,
            TransferContext transferContext)
            throws Exception;
}