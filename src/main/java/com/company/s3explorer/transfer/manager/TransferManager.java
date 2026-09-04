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
                        buildOperationGroupName(
                                "Copy",
                                keySource));

        configureGroupCompletion(
                group,
                repositoryName,
                bucket,
                keySource,
                false);

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
                group);

        group.markProductionCompleted();

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

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

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
                group);
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
                        buildOperationGroupName(
                                "Move",
                                keySource));

        configureGroupCompletion(
                group,
                repositoryName,
                bucket,
                keySource,
                true);

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
                group);

        group.markProductionCompleted();

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

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

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
                group);
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

        TransferGroup group =
                createFolderOperationGroup(
                        "COPY",
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix);

        submitFolderCopy(
                repositoryName,
                sourceBucket,
                sourcePrefix,
                targetRepositoryName,
                targetBucket,
                targetPrefix,
                group);
    }

    /**
     * Submits a folder copy producer to an existing transfer group.
     *
     * The caller owns the shared group lifecycle.
     */
    public void submitFolderCopy(
            String repositoryName,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryName,
            String targetBucket,
            String targetPrefix,
            TransferGroup group) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        /*
         * Group completion must be configured before
         * the producer starts creating tasks.
         */
        configureGroupCompletion(
                group,
                repositoryName,
                sourceBucket,
                sourcePrefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryName,
                sourceBucket,
                sourcePrefix,
                false);

        producerExecutor.submit(
                new FolderCopyProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix,
                        group)
        );
    }

    public void submitFolderMove(
            String repositoryName,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryName,
            String targetBucket,
            String targetPrefix) {

        TransferGroup group =
                createFolderOperationGroup(
                        "MOVE",
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix);

        submitFolderMove(
                repositoryName,
                sourceBucket,
                sourcePrefix,
                targetRepositoryName,
                targetBucket,
                targetPrefix,
                group);
    }

    /**
     * Submits a folder move producer to an existing transfer group.
     *
     * The caller owns the shared group lifecycle.
     */
    public void submitFolderMove(
            String repositoryName,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryName,
            String targetBucket,
            String targetPrefix,
            TransferGroup group) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        /*
         * Group completion must be configured before
         * the producer starts creating tasks.
         */
        configureGroupCompletion(
                group,
                repositoryName,
                sourceBucket,
                sourcePrefix,
                true);

        transferContext.publishGroupUpdated(
                group,
                repositoryName,
                sourceBucket,
                sourcePrefix,
                true);
        
        producerExecutor.submit(
                new FolderMoveProducer(
                        transferContext,
                        queue,
                        repositoryName,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryName,
                        targetBucket,
                        targetPrefix,
                        group)
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

    /**
     * Configures the completion callback for a group.
     *
     * This method must be called once for a batch group,
     * before the group's tasks or producers are submitted.
     */
    public void configureGroupCompletion(
            TransferGroup group,
            String repositoryName,
            String bucket,
            String prefix,
            boolean sourceRefreshRequired) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

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
                                    group.getCancelled() +
                                    " sourceRefreshRequired=" +
                                    sourceRefreshRequired);

                    transferContext.publishGroupCompleted(
                            group,
                            repositoryName,
                            bucket,
                            prefix,
                            sourceRefreshRequired);
                });
    }

    /**
     * Backward-compatible group completion configuration.
     *
     * Existing callers use source refresh by default.
     */
    public void configureGroupCompletion(
            TransferGroup group,
            String repositoryName,
            String bucket,
            String prefix) {

        configureGroupCompletion(
                group,
                repositoryName,
                bucket,
                prefix,
                true);
    }

    /**
     * Adds a task to an already configured group.
     *
     * This method intentionally does not mark production
     * as completed. The caller owns the group production
     * lifecycle.
     */
    private void submitGroupedTask(
            TransferTask task,
            TransferGroup group) {

        if (task == null) {
            throw new IllegalArgumentException(
                    "Transfer task must not be null");
        }

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        queue.add(task);
    }

    public TransferGroup createOperationGroup(
            String operationName) {

        return new TransferGroup(
                UUID.randomUUID(),
                operationName);
    }

    private TransferGroup createFolderOperationGroup(
            String operation,
            String sourceRepository,
            String sourceBucket,
            String sourcePrefix,
            String targetRepository,
            String targetBucket,
            String targetPrefix) {

        String displayName =
                S3Util.extractFolderName(sourcePrefix);

        if (displayName == null
                || displayName.isBlank()) {

            displayName = sourcePrefix;
        }

        String source =
                buildGroupLocation(
                        sourceRepository,
                        sourceBucket,
                        sourcePrefix);

        String target =
                buildGroupLocation(
                        targetRepository,
                        targetBucket,
                        targetPrefix);

        return new TransferGroup(
                UUID.randomUUID(),
                displayName,
                operation,
                source,
                target);
    }

    private String buildGroupLocation(
            String repository,
            String bucket,
            String prefix) {

        StringBuilder value =
                new StringBuilder();

        if (repository != null
                && !repository.isBlank()) {

            value.append(repository);
        }

        if (bucket != null
                && !bucket.isBlank()) {

            if (!value.isEmpty()) {
                value.append("/");
            }

            value.append(bucket);
        }

        if (prefix != null
                && !prefix.isBlank()) {

            if (!value.isEmpty()
                    && !value.toString().endsWith("/")) {

                value.append("/");
            }

            value.append(prefix);
        }

        return value.toString();
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

    private String buildOperationGroupName(
            String operation,
            String key) {

        String name = S3Util.extractFolderName(key);

        if (name == null
                || name.isBlank()) {

            name = S3Util.extractFileName(key);
        }

        if (name == null
                || name.isBlank()) {

            name = key;
        }

        return operation
                + " — "
                + name;
    }
}