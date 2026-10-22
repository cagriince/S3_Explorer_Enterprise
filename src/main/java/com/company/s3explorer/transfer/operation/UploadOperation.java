
package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;

public class UploadOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(
            TransferRuntime runtime,
            TransferContext transferContext)
            throws Exception {

        TransferTask task = runtime.getTask();

        updateProgressPercent(
                runtime,
                transferContext,
                0);

        try {
            if (task.getEncryptionConfig() != null) {

                transferContext
                        .getService(task.getTargetRepositoryId())
                        .uploadEncryptedFile(
                                task.getTargetBucket(),
                                task.getTargetObjectKey(),
                                task.getLocalPath(),
                                task.getEncryptionConfig(),
                                task.isOverwrite(),
                                createProgressListener(
                                        runtime,
                                        transferContext));

            } else {

                transferContext
                        .getService(task.getTargetRepositoryId())
                        .uploadFile(
                                task.getTargetBucket(),
                                task.getTargetObjectKey(),
                                task.getLocalPath(),
                                task.isOverwrite(),
                                createProgressListener(
                                        runtime,
                                        transferContext));
            }
        } finally {
            if (!runtime.isCancelRequested()) {
                updateProgressCompleted(
                        runtime,
                        transferContext);
            }
        }
    }
}
