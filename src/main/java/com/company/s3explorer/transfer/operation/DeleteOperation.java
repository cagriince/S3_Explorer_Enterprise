package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;

public class DeleteOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(TransferRuntime runtime, TransferContext transferContext) throws Exception {
        updateProgressPercent(runtime, transferContext, 10);

        try {
            transferContext.getService(runtime.getTask().getRepositoryName()).deleteObject(
                    runtime.getTask().getBucket(),
                    runtime.getTask().getObjectKey());
        }
        finally {
            updateProgressCompleted(runtime, transferContext);
        }
    }
}