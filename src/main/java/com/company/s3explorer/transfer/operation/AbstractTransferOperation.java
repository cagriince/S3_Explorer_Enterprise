package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.service.TransferProgressListener;
import com.company.s3explorer.transfer.model.TransferGroup;

import java.time.Instant;
import java.util.concurrent.CancellationException;

public abstract class AbstractTransferOperation implements TransferOperation {

    @Override
    public final void execute(TransferRuntime runtime, TransferContext transferContext) throws Exception {
        TransferGroup group = runtime.getTask().getGroup();
        if (group != null) {
            group.running();
        }
        transferContext.publishRunning(runtime);

        try {
            checkCancelled(runtime);
            doExecute(runtime, transferContext);
            checkCancelled(runtime);
            if (group != null) {
                group.completed();
            }
            transferContext.publishCompleted(runtime);
        }
        catch (CancellationException ex) {
            if (group != null) {
                group.cancelled();
            }
            transferContext.publishCancelled(runtime);
        }
        catch (Exception ex) {
            if (group != null) {
                group.failed();
            }
            transferContext.publishFailed(runtime, ex);
            throw ex;
        }
    }

    protected void checkCancelled(TransferRuntime runtime) throws CancellationException {
        if (runtime.isCancelRequested()) {
            throw new CancellationException();
        }
    }

    protected TransferProgressListener createProgressListener(TransferRuntime runtime, TransferContext transferContext) {
        return (transferred, total) -> {
            checkCancelled(runtime);
            runtime.updateProgress(transferred, total);
            if (runtime.shouldPublishUi(100)) {
                transferContext.publishProgress(runtime);
            }
        };
    }

    protected void updateProgress(TransferRuntime runtime, TransferContext transferContext, long transferred, long total) {
        checkCancelled(runtime);
        runtime.updateProgress(transferred, total);
        if (runtime.shouldPublishUi(100)) {
            transferContext.publishProgress(runtime);
        }
    }

    protected void updateProgressCompleted(TransferRuntime runtime, TransferContext transferContext) {
        runtime.progressCompleted();
        runtime.forceNextUiPublish();
        transferContext.publishProgress(runtime);
    }

    protected void updateProgressPercent(TransferRuntime runtime, TransferContext transferContext, int percent) {
        checkCancelled(runtime);
        runtime.updateProgress(percent, 100);
        if (runtime.shouldPublishUi(100)) {
            transferContext.publishProgress(runtime);
        }
    }

    protected abstract void doExecute(
            TransferRuntime runtime,
            TransferContext transferContext)
            throws Exception;
}
