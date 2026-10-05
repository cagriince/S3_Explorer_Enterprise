package com.company.s3explorer.transfer.operation;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;

public class MoveOperation extends AbstractTransferOperation {

    @Override
    protected void doExecute(
            TransferRuntime runtime,
            TransferContext transferContext) throws Exception {

        TransferTask task =
                runtime.getTask();

        if (task.getRepositoryId()
                .equals(task.getTargetRepositoryId())) {

            updateProgressPercent(
                    runtime,
                    transferContext,
                    10);

            try {

                if (task.isOverwrite()) {

                    transferContext
                            .getService(
                                    task.getRepositoryId())
                            .copyObjectOverwrite(
                                    task.getBucket(),
                                    task.getObjectKey(),
                                    task.getTargetBucket(),
                                    task.getTargetObjectKey());

                } else {

                    transferContext
                            .getService(
                                    task.getRepositoryId())
                            .copyObject(
                                    task.getBucket(),
                                    task.getObjectKey(),
                                    task.getTargetBucket(),
                                    task.getTargetObjectKey());
                }

                updateProgressPercent(
                        runtime,
                        transferContext,
                        70);

                transferContext
                        .getService(
                                task.getRepositoryId())
                        .deleteObject(
                                task.getBucket(),
                                task.getObjectKey());

            }
            finally {

                updateProgressCompleted(
                        runtime,
                        transferContext);
            }

        } else {

            // Cross repository operation
            updateProgressPercent(
                    runtime,
                    transferContext,
                    0);

            try {

                transferContext
                        .getService(
                                task.getRepositoryId())
                        .copyObjectBetweenRepositories(
                                task.getBucket(),
                                task.getObjectKey(),
                                transferContext
                                        .getService(
                                                task.getTargetRepositoryId())
                                        .getClient(),
                                task.getTargetBucket(),
                                task.getTargetObjectKey(),
                                createProgressListener(
                                        runtime,
                                        transferContext));

                updateProgressPercent(
                        runtime,
                        transferContext,
                        70);

                transferContext
                        .getService(
                                task.getRepositoryId())
                        .deleteObject(
                                task.getBucket(),
                                task.getObjectKey());

            }
            finally {

                updateProgressCompleted(
                        runtime,
                        transferContext);
            }
        }
    }
}