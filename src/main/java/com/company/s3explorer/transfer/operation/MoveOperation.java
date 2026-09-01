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

        if (task.getRepositoryName()
                .equals(task.getTargetRepositoryName())) {

            updateProgressPercent(
                    runtime,
                    transferContext,
                    10);

            try {

                if (task.isOverwrite()) {

                    transferContext
                            .getService(
                                    task.getRepositoryName())
                            .copyObjectOverwrite(
                                    task.getBucket(),
                                    task.getObjectKey(),
                                    task.getTargetBucket(),
                                    task.getTargetObjectKey());

                } else {

                    transferContext
                            .getService(
                                    task.getRepositoryName())
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
                                task.getRepositoryName())
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
                                task.getRepositoryName())
                        .copyObjectBetweenRepositories(
                                task.getBucket(),
                                task.getObjectKey(),
                                transferContext
                                        .getService(
                                                task.getTargetRepositoryName())
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
                                task.getRepositoryName())
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