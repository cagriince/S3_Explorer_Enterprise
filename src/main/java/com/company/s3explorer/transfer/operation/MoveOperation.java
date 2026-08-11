package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;

public class MoveOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(TransferRuntime runtime, TransferContext transferContext) throws Exception {
        TransferTask task = runtime.getTask();
        if (task.getRepositoryName().equals(task.getTargetRepositoryName())) {
            updateProgressPercent(runtime, transferContext, 10);

            try {
                transferContext.getService(task.getRepositoryName()).copyObject(
                        runtime.getTask().getBucket(),
                        runtime.getTask().getObjectKey(),
                        runtime.getTask().getTargetBucket(),
                        runtime.getTask().getTargetObjectKey());

                updateProgressPercent(runtime, transferContext, 70);

                transferContext.getService(task.getRepositoryName()).deleteObject(
                        runtime.getTask().getBucket(),
                        runtime.getTask().getObjectKey());
            }
            finally {
                updateProgressCompleted(runtime, transferContext);
            }
        }
        else {
            // Cross repository operation
            updateProgressPercent(runtime, transferContext, 0);

            try {
                transferContext.getService(task.getRepositoryName()).copyObjectBetweenRepositories(
                        runtime.getTask().getBucket(),
                        runtime.getTask().getObjectKey(),
                        transferContext.getService(runtime.getTask().getTargetRepositoryName()).getClient(),
                        runtime.getTask().getTargetBucket(),
                        runtime.getTask().getTargetObjectKey(),
                        createProgressListener(runtime, transferContext));

                updateProgressPercent(runtime, transferContext, 70);

                transferContext.getService(task.getRepositoryName()).deleteObject(
                        runtime.getTask().getBucket(),
                        runtime.getTask().getObjectKey());
            }
            finally {
                updateProgressCompleted(runtime, transferContext);
            }
        }
    }
}
