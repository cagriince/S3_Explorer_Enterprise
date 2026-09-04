package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
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

            if (group != null) {

                group.completed();

                publishGroupUpdated(
                        group,
                        transferContext);
            }

            transferContext.publishCompleted(
                    runtime);

            log.debug(
                    "[OPERATION PUBLISHED COMPLETED] {}",
                    runtime.getTask().getId());

        }
        catch (CancellationException ex) {

            if (group != null) {

                group.cancelled();

                publishGroupUpdated(
                        group,
                        transferContext);
            }

            transferContext.publishCancelled(
                    runtime);
        }
        catch (Exception ex) {

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
                "MOVE".equalsIgnoreCase(
                        group.getOperation()));
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

    protected void updateProgress(
            TransferRuntime runtime,
            TransferContext transferContext,
            long transferred,
            long total) {

        checkCancelled(runtime);

        runtime.updateProgress(
                transferred,
                total);

        if (runtime.shouldPublishUi(100)) {

            transferContext.publishProgress(
                    runtime);
        }
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