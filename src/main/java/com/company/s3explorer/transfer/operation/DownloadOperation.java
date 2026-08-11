package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;

public class DownloadOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(TransferRuntime runtime, TransferContext transferContext) throws Exception {
        updateProgressPercent(runtime, transferContext, 0);

        try {
            transferContext.getService(runtime.getTask().getRepositoryName()).downloadFile(
                    runtime.getTask().getBucket(),
                    runtime.getTask().getObjectKey(),
                    runtime.getTask().getLocalPath(),
                    createProgressListener(runtime, transferContext));
        }
        finally {
            updateProgressCompleted(runtime, transferContext);
        }
    }

}
