package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;

public class UploadOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(
            TransferRuntime runtime,
            TransferContext transferContext)
            throws Exception {

        updateProgressPercent(
                runtime,
                transferContext,
                0);

        try {
            if (runtime.getTask().getEncryptionConfig() != null) {

                transferContext
                        .getService(
                                runtime.getTask()
                                        .getTargetRepositoryId())
                        .uploadEncryptedFile(
                                runtime.getTask()
                                        .getTargetBucket(),
                                runtime.getTask()
                                        .getTargetObjectKey(),
                                runtime.getTask()
                                        .getLocalPath(),
                                runtime.getTask()
                                        .getEncryptionConfig(),
                                createProgressListener(
                                        runtime,
                                        transferContext));

            }
            else {

                transferContext
                        .getService(
                                runtime.getTask()
                                        .getTargetRepositoryId())
                        .uploadFile(
                                runtime.getTask()
                                        .getTargetBucket(),
                                runtime.getTask()
                                        .getTargetObjectKey(),
                                runtime.getTask()
                                        .getLocalPath(),
                                createProgressListener(
                                        runtime,
                                        transferContext));
            }
        }
        finally {
            if (!runtime.isCancelRequested()) {
                updateProgressCompleted(
                        runtime,
                        transferContext);
            }
        }
    }
}
