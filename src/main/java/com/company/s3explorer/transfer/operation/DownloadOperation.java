package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;

public class DownloadOperation extends AbstractTransferOperation {

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
                                        .getRepositoryName())
                        .downloadDecryptedFile(
                                runtime.getTask()
                                        .getBucket(),
                                runtime.getTask()
                                        .getObjectKey(),
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
                                        .getRepositoryName())
                        .downloadFile(
                                runtime.getTask()
                                        .getBucket(),
                                runtime.getTask()
                                        .getObjectKey(),
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