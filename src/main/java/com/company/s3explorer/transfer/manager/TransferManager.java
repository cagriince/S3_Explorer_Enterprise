package com.company.s3explorer.transfer.manager;

import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.service.S3ExplorerService;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.factory.TransferOperationFactory;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.producer.*;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import com.company.s3explorer.util.S3Util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferManager {

    private final TransferQueue queue;
    private final TransferContext transferContext;
    private final TransferOperationFactory operationFactory;
    private final ProducerExecutor producerExecutor;
    private final ExecutorService cancellationExecutor;

    public TransferManager(
            S3ClientManager clientManager,
            TransferQueue queue,
            TransferEventBus eventBus,
            ProducerExecutor producerExecutor) {

        this.queue = queue;
        this.producerExecutor = producerExecutor;

        transferContext =
                new TransferContext(
                        clientManager,
                        eventBus);

        operationFactory =
                new TransferOperationFactory();

        cancellationExecutor =
                Executors.newSingleThreadExecutor(
                        runnable -> {

                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "transfer-cancellation");

                            thread.setDaemon(true);

                            return thread;
                        });
    }

    public boolean cancel(UUID taskId) {
        return queue.cancel(taskId);
    }

    public boolean cancelProducer(
            ProducerRuntime runtime) {

        return producerExecutor.cancel(runtime);
    }

    public void cancelAll() {

        cancellationExecutor.submit(() -> {

            System.out.println(
                    "[CANCEL ALL START]");

            queue.beginCancelAll();

            try {

                producerExecutor.cancelAll();

                queue.cancelAll();

            }
            finally {

                queue.endCancelAll();

                System.out.println(
                        "[CANCEL ALL FINISHED]");
            }
        });
    }

    public void submitUpload(
            String repositoryName,
            String bucket,
            String key,
            Path localFile,
            long size) {

        submit(
                TransferTask.upload()
                        .targetRepositoryName(
                                repositoryName)
                        .targetBucket(
                                bucket)
                        .targetObjectKey(
                                key)
                        .localPath(
                                localFile)
                        .size(size)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .build()
        );
    }

    public void submitDownload(
            String repositoryName,
            String bucket,
            String key,
            Path localFile,
            long size) {

        Path target =
                localFile.resolve(
                        S3Util.extractFileName(key));

        submit(
                TransferTask.download()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .localPath(
                                target)
                        .size(size)
                        .affectsObjectList(false)
                        .affectsFolderTree(false)
                        .build()
        );
    }

    public void submitDelete(
            String repositoryName,
            String bucket,
            String key,
            long size) {

        submit(
                TransferTask.delete()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .size(size)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .build()
        );
    }

    public TransferGroup submitCopy(
            String repositoryName,
            String bucket,
            String keySource,
            String targetRepositoryName,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite) {

        TransferGroup group =
                createOperationGroup(
                        "Copy");

        TransferTask task =
                TransferTask.copy()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryName(
                                targetRepositoryName)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group,
                repositoryName,
                bucket,
                keySource);

        return group;
    }

    public void submitCopy(
            String repositoryName,
            String bucket,
            String keySource,
            String targetRepositoryName,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite,
            TransferGroup group) {

        TransferTask task =
                TransferTask.copy()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryName(
                                targetRepositoryName)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group,
                repositoryName,
                bucket,
                keySource);
    }
    
    public TransferGroup submitMove(
            String repositoryName,
            String bucket,
            String keySource,
            String targetRepositoryName,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite) {

        TransferGroup group =
                createOperationGroup(
                        "Move");

        TransferTask task =
                TransferTask.move()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryName(
                                targetRepositoryName)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(true)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group,
                repositoryName,
                bucket,
                keySource);

        return group;
    }

    public void submitMove(
            String repositoryName,
            String bucket,
            String keySource,
            String targetRepositoryName,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite,
            TransferGroup group) {

        TransferTask task =
                TransferTask.move()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryName(
                                targetRepositoryName)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(true)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group,
                repositoryName,
                bucket,
                keySource);
    }
    
    public void submitCreateFolder(
            String repositoryName,
            String bucket,
            String key,
            String prefix) {

        submit(
                TransferTask.createFolder()
                        .repositoryName(
                                repositoryName)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .addRefreshPrefix(
                                new RefreshTreeNode(
                                        prefix,
                                        RefreshTreeOperation.ADD))
                        .size(0)
                        .affectsObjectList(true)
                        .affectsFolderTree(true)
                        .build()
        );
    }

    public void submitFolderDelete(
            String repositoryName,
            String bucket,
            String key) {

        producerExecutor.submit(
                new FolderDeleteProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        bucket,
                        key)
        );
    }

    public void submitFolderDownload(
            String repositoryName,
            String bucket,
            String prefix,
            Path localFolder) {

        producerExecutor.submit(
                new FolderDownloadProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        bucket,
                        prefix,
                        localFolder)
        );
    }

    public void submitFolderUpload(
            String repositoryName,
            String bucket,
            String targetPrefix,
            Path folder)
            throws IOException {

        producerExecutor.submit(
                new FolderUploadProducer(
                        queue,
                        repositoryName,
                        bucket,
                        targetPrefix,
                        folder)
        );
    }

    public void submitFolderCopy(
            String repositoryName,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryName,
            String targetBucket,
            String targetPrefix) {

        producerExecutor.submit(
                new FolderCopyProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix)
        );
    }

    public void submitFolderMove(
            String repositoryName,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryName,
            String targetBucket,
            String targetPrefix) {

        producerExecutor.submit(
                new FolderMoveProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix)
        );
    }

    public void submitFolderRename(
            String repositoryName,
            String bucket,
            String prefix,
            String targetPrefix) {

        producerExecutor.submit(
                new FolderRenameProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        bucket,
                        prefix,
                        targetPrefix)
        );
    }

    public void close() {

        cancellationExecutor.shutdownNow();

        producerExecutor.close();
    }

    private void submit(
            TransferTask task) {

        queue.add(task);
    }

    private void submitGroupedTask(
            TransferTask task,
            TransferGroup group,
            String repositoryName,
            String bucket,
            String prefix) {

        group.setCompletionCallback(
                () -> {

                    System.out.println(
                            "[TRANSFER GROUP CALLBACK] " +
                                    "group=" +
                                    group.getDisplayName() +
                                    " finished=" +
                                    group.isFinished() +
                                    " successful=" +
                                    group.isFullySuccessful() +
                                    " queued=" +
                                    group.getQueued() +
                                    " running=" +
                                    group.getRunning() +
                                    " completed=" +
                                    group.getCompleted() +
                                    " failed=" +
                                    group.getFailed() +
                                    " cancelled=" +
                                    group.getCancelled());

                    transferContext.publishGroupCompleted(
                            group,
                            repositoryName,
                            bucket,
                            prefix);
                });

        queue.add(task);

        /*
         * There will be no additional tasks for this
         * single-task operation.
         *
         * The group may already have completed by the
         * time this method is reached, so markProductionCompleted()
         * is deliberately called after queue.add().
         */
        group.markProductionCompleted();
    }

    private TransferGroup createOperationGroup(
            String operationName) {

        return new TransferGroup(
                UUID.randomUUID(),
                operationName);
    }

    private TransferGroup createGroup(
            UUID id,
            String name) {

        return new TransferGroup(
                id,
                name);
    }

    private S3ExplorerService getService(
            String repositoryName) {

        return transferContext.getService(
                repositoryName);
    }
}