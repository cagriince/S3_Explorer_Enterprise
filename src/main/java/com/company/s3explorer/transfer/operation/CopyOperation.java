package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;

public class CopyOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(TransferRuntime runtime, TransferContext transferContext) throws Exception {
        TransferTask task = runtime.getTask();
        if (task.getRepositoryName().equals(task.getTargetRepositoryName())) {
            updateProgressPercent(runtime, transferContext, 10);

            try {
                if (task.isOverwrite()) {
                    transferContext
                            .getService(task.getRepositoryName())
                            .copyObjectOverwrite(
                                    task.getBucket(),
                                    task.getObjectKey(),
                                    task.getTargetBucket(),
                                    task.getTargetObjectKey());

                } else {
                    transferContext
                            .getService(task.getRepositoryName())
                            .copyObject(
                                    task.getBucket(),
                                    task.getObjectKey(),
                                    task.getTargetBucket(),
                                    task.getTargetObjectKey());
                }
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
            }
            finally {
                updateProgressCompleted(runtime, transferContext);
            }
        }

    }
}