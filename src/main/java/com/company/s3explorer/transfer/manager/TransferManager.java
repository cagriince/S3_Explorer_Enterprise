package com.company.s3explorer.transfer.manager;

import com.company.s3explorer.security.EncryptionConfig;
import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.producer.*;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import com.company.s3explorer.util.S3Util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferManager {

    private final TransferQueue queue;
    private final TransferContext transferContext;
    private final ProducerExecutor producerExecutor;
    private final ExecutorService cancellationExecutor;

    private final Map<UUID, ProducerRuntime> groupProducers =
            new ConcurrentHashMap<>();

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

    public boolean cancelGroup(TransferGroup group) {

        if (group == null
                || group.getId() == null) {

            return false;
        }

        UUID groupId =
                group.getId();

        /*
         * First mark the group as cancelled and
         * cancel already queued/running transfer tasks.
         *
         * queue.cancelGroup() calls
         * group.requestCancellation(), so any task
         * produced concurrently after this point
         * will also be rejected by TransferQueue.
         */
        boolean cancelled =
                queue.cancelGroup(group);

        /*
         * The producer itself is separate from the
         * transfer tasks. Cancel it as well so that
         * S3 listing/production stops.
         */
        ProducerRuntime producerRuntime =
                groupProducers.remove(groupId);

        if (producerRuntime != null) {

            boolean producerCancelled =
                    producerExecutor.cancel(
                            producerRuntime);

            cancelled =
                    cancelled
                            || producerCancelled;
        }

        return cancelled;
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
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size) {

        submit(
                TransferTask.upload()
                        .targetRepositoryId(
                                repositoryId)
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

    public void submitUploadEncrypted(
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size,
            EncryptionConfig encryptionConfig) {

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        submit(
                TransferTask.upload()
                        .targetRepositoryId(
                                repositoryId)
                        .targetBucket(
                                bucket)
                        .targetObjectKey(
                                key)
                        .localPath(
                                localFile)
                        .size(size)
                        .encryptionConfig(
                                encryptionConfig)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .build()
        );
    }
    
    public void submitDownload(
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size) {

        Path target =
                localFile.resolve(
                        S3Util.extractFileName(key));

        submit(
                TransferTask.download()
                        .repositoryId(
                                repositoryId)
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

    public void submitDownloadDecrypted(
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size,
            EncryptionConfig encryptionConfig) {

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        Path target =
                localFile.resolve(
                        S3Util.extractFileName(key));

        submit(
                TransferTask.download()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .localPath(
                                target)
                        .size(size)
                        .encryptionConfig(
                                encryptionConfig)
                        .affectsObjectList(false)
                        .affectsFolderTree(false)
                        .build()
        );
    }

    public void submitDownloadDecrypted(
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size,
            EncryptionConfig encryptionConfig,
            TransferGroup group) {

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        Path target =
                localFile.resolve(
                        S3Util.extractFileName(key));

        TransferTask task =
                TransferTask.download()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .localPath(
                                target)
                        .size(size)
                        .encryptionConfig(
                                encryptionConfig)
                        .affectsObjectList(false)
                        .affectsFolderTree(false)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group);
    }
    
    public void submitDownload(
            String repositoryId,
            String bucket,
            String key,
            Path localFile,
            long size,
            TransferGroup group) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        Path target =
                localFile.resolve(
                        S3Util.extractFileName(key));

        TransferTask task =
                TransferTask.download()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .localPath(
                                target)
                        .size(size)
                        .affectsObjectList(false)
                        .affectsFolderTree(false)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group);
    }

    public TransferGroup createDownloadGroup(
            String repositoryId,
            String bucket,
            String sourcePrefix,
            String groupName,
            Path destination) {

        TransferGroup group =
                createOperationGroup(
                        TransferType.DOWNLOAD_GROUP,
                        groupName,
                        repositoryId,
                        bucket,
                        sourcePrefix,
                        null,
                        null,
                        destination.toString());

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                sourcePrefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                sourcePrefix,
                false);

        return group;
    }
    
    public TransferGroup submitBulkDownload(
            String repositoryId,
            String bucket,
            java.util.List<String> objectKeys,
            Path localFolder) {

        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "Object key list must not be empty.");
        }

        if (localFolder == null) {
            throw new IllegalArgumentException(
                    "Local folder must not be null.");
        }

        String firstKey = objectKeys.stream()
                .filter(key ->
                        key != null
                                && !key.isBlank())
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Object key list must contain at least one valid key."));

        String displayName =
                S3Util.isFolder(firstKey)
                        ? S3Util.extractFolderName(firstKey)
                        : S3Util.extractFileName(firstKey);

        if (displayName == null
                || displayName.isBlank()) {

            displayName = firstKey;
        }

        if (objectKeys.stream()
                .filter(key ->
                        key != null
                                && !key.isBlank())
                .count() > 1) {

            displayName += " and others";
        }

        TransferGroup group =
                createOperationGroup(
                        TransferType.DOWNLOAD_GROUP,
                        displayName,
                        repositoryId,
                        bucket,
                        firstKey,
                        null,
                        null,
                        localFolder.toString());

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                firstKey,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                firstKey,
                false);

        submitGroupProducer(
                group,
                new BulkDownloadProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        objectKeys,
                        localFolder,
                        null,
                        group,
                        firstKey)
        );
        
        return group;
    }

    public TransferGroup submitBulkDownloadDecrypted(
            String repositoryId,
            String bucket,
            java.util.List<String> objectKeys,
            Path localFolder,
            EncryptionConfig encryptionConfig) {

        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "Object key list must not be empty.");
        }

        if (localFolder == null) {
            throw new IllegalArgumentException(
                    "Local folder must not be null.");
        }

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        String firstKey = objectKeys.stream()
                .filter(key ->
                        key != null
                                && !key.isBlank())
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Object key list must contain at least one valid key."));

        String displayName =
                S3Util.isFolder(firstKey)
                        ? S3Util.extractFolderName(firstKey)
                        : S3Util.extractFileName(firstKey);

        if (displayName == null
                || displayName.isBlank()) {

            displayName = firstKey;
        }

        if (objectKeys.stream()
                .filter(key ->
                        key != null
                                && !key.isBlank())
                .count() > 1) {

            displayName += " and others";
        }

        TransferGroup group =
                createOperationGroup(
                        TransferType.DOWNLOAD_GROUP,
                        displayName,
                        repositoryId,
                        bucket,
                        firstKey,
                        null,
                        null,
                        localFolder.toString());

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                firstKey,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                firstKey,
                false);

        submitGroupProducer(
                group,
                new BulkDownloadProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        objectKeys,
                        localFolder,
                        encryptionConfig,
                        group,
                        firstKey)
        );

        return group;
    }
    
    public void submitDelete(
            String repositoryId,
            String bucket,
            String key,
            long size) {

        submit(
                TransferTask.delete()
                        .repositoryId(
                                repositoryId)
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
    
    public void submitDelete(
            String repositoryId,
            String bucket,
            String key,
            long size,
            TransferGroup group) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        TransferTask task =
                TransferTask.delete()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                key)
                        .size(size)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .group(group)
                        .build();

        submitGroupedTask(
                task,
                group);
    }

    public void submitCopy(
            String repositoryId,
            String bucket,
            String keySource,
            String targetRepositoryId,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite) {

        submit(
                TransferTask.copy()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryId(
                                targetRepositoryId)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(false)
                        .build()
              );
    }

    public void submitCopy(
            String repositoryId,
            String bucket,
            String keySource,
            String targetRepositoryId,
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
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryId(
                                targetRepositoryId)
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

    public void submitMove(
            String repositoryId,
            String bucket,
            String keySource,
            String targetRepositoryId,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite) {

        submit(
                TransferTask.move()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryId(
                                targetRepositoryId)
                        .targetBucket(
                                targetBucket)
                        .targetObjectKey(
                                keyTarget)
                        .size(size)
                        .overwrite(overwrite)
                        .affectsObjectList(true)
                        .affectsFolderTree(true)
                        .build()
              );
    }

    public void submitMove(
            String repositoryId,
            String bucket,
            String keySource,
            String targetRepositoryId,
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
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryId(
                                targetRepositoryId)
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

    public TransferGroup submitRename(
            String repositoryId,
            String bucket,
            String keySource,
            String targetRepositoryId,
            String targetBucket,
            String keyTarget,
            long size,
            boolean overwrite) {

        TransferGroup group =
                createObjectOperationGroup(
                        TransferType.RENAME,
                        repositoryId,
                        bucket,
                        keySource,
                        targetRepositoryId,
                        targetBucket,
                        keyTarget);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                keySource,
                true);

        TransferTask task =
                TransferTask.rename()
                        .repositoryId(
                                repositoryId)
                        .bucket(
                                bucket)
                        .objectKey(
                                keySource)
                        .targetRepositoryId(
                                targetRepositoryId)
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
    
    public void submitCreateFolder(
            String repositoryId,
            String bucket,
            String key,
            String prefix) {

        submit(
                TransferTask.createFolder()
                        .repositoryId(
                                repositoryId)
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

    public TransferGroup submitFolderDelete(
            String repositoryId,
            String bucket,
            String prefix) {

        TransferGroup group =
                createFolderOperationGroup(
                        TransferType.DELETE_GROUP,
                        repositoryId,
                        bucket,
                        prefix,
                        repositoryId,
                        bucket,
                        prefix);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                prefix,
                true);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        submitGroupProducer(
                group,
                new FolderDeleteProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        prefix,
                        group)
        );

        return group;
    }

    public void submitFolderDelete(
            String repositoryId,
            String bucket,
            String prefix,
            TransferGroup group) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        submitGroupProducer(
                group,
                new FolderDeleteProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        prefix,
                        group)
                           );
    }
    
    public void submitFolderDownload(
            String repositoryId,
            String bucket,
            String prefix,
            Path localFolder) {

        TransferGroup group =
                new TransferGroup(
                        UUID.randomUUID(),
                        S3Util.extractFolderName(prefix),
                        TransferType.DOWNLOAD_GROUP,
                        buildGroupLocation(
                                repositoryId,
                                bucket,
                                prefix),
                        localFolder.toString(),
                        repositoryId,
                        bucket,
                        prefix,
                        null,
                        null,
                        localFolder.toString(),
                        true);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        submitGroupProducer(
                group,
                new FolderDownloadProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        prefix,
                        localFolder,
                        null,
                        group)
        );
    }

    public void submitFolderDownloadDecrypted(
            String repositoryId,
            String bucket,
            String prefix,
            Path localFolder,
            EncryptionConfig encryptionConfig) {

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        TransferGroup group =
                new TransferGroup(
                        UUID.randomUUID(),
                        S3Util.extractFolderName(prefix),
                        TransferType.DOWNLOAD_GROUP,
                        buildGroupLocation(
                                repositoryId,
                                bucket,
                                prefix),
                        localFolder.toString(),
                        repositoryId,
                        bucket,
                        prefix,
                        null,
                        null,
                        localFolder.toString(),
                        true);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                prefix,
                false);

        submitGroupProducer(
                group,
                new FolderDownloadProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        prefix,
                        localFolder,
                        encryptionConfig,
                        group)
        );
    }

    public void submitFolderUpload(
            String repositoryId,
            String bucket,
            String targetPrefix,
            Path folder)
            throws IOException {

        String displayName =
                folder.getFileName() != null
                        ? folder.getFileName().toString()
                        : folder.toString();

        TransferGroup group =
                new TransferGroup(
                        UUID.randomUUID(),
                        displayName,
                        TransferType.UPLOAD_GROUP,
                        folder.toString(),
                        buildGroupLocation(
                                repositoryId,
                                bucket,
                                targetPrefix + displayName + "/"),
                        repositoryId,
                        bucket,
                        targetPrefix,
                        repositoryId,
                        bucket,
                        targetPrefix + displayName + "/",
                        true);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                targetPrefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                targetPrefix,
                false);

        submitGroupProducer(
                group,
                new FolderUploadProducer(
                        queue,
                        repositoryId,
                        bucket,
                        targetPrefix,
                        folder,
                        group)
        );
    }
    
    public void submitFolderCopy(
            String repositoryId,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryId,
            String targetBucket,
            String targetPrefix) {

        TransferGroup group =
                createFolderOperationGroup(
                        TransferType.COPY_GROUP,
                        repositoryId,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryId,
                        targetBucket,
                        targetPrefix);

        submitFolderCopy(
                repositoryId,
                sourceBucket,
                sourcePrefix,
                targetRepositoryId,
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
            String repositoryId,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryId,
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
                repositoryId,
                sourceBucket,
                sourcePrefix,
                false);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                sourceBucket,
                sourcePrefix,
                false);

        submitGroupProducer(
                group,
                new FolderCopyProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryId,
                        targetBucket,
                        targetPrefix,
                        group)
        );
    }

    public void submitFolderMove(
            String repositoryId,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryId,
            String targetBucket,
            String targetPrefix) {

        TransferGroup group =
                createFolderOperationGroup(
                        TransferType.MOVE_GROUP,
                        repositoryId,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryId,
                        targetBucket,
                        targetPrefix);

        submitFolderMove(
                repositoryId,
                sourceBucket,
                sourcePrefix,
                targetRepositoryId,
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
            String repositoryId,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryId,
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
                repositoryId,
                sourceBucket,
                sourcePrefix,
                true);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                sourceBucket,
                sourcePrefix,
                true);

        submitGroupProducer(
                group,
                new FolderMoveProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        sourceBucket,
                        sourcePrefix,
                        targetRepositoryId,
                        targetBucket,
                        targetPrefix,
                        group)
        );
    }

    public void submitFolderRename(
            String repositoryId,
            String bucket,
            String prefix,
            String targetPrefix) {

        TransferGroup group =
                createFolderOperationGroup(
                        TransferType.RENAME_GROUP,
                        repositoryId,
                        bucket,
                        prefix,
                        repositoryId,
                        bucket,
                        targetPrefix);

        configureGroupCompletion(
                group,
                repositoryId,
                bucket,
                prefix,
                true);

        transferContext.publishGroupUpdated(
                group,
                repositoryId,
                bucket,
                prefix,
                true);

        submitGroupProducer(
                group,
                new FolderRenameProducer(
                        transferContext,
                        queue,
                        repositoryId,
                        bucket,
                        prefix,
                        targetPrefix,
                        group)
        );
    }

    public void close() {

        cancellationExecutor.shutdownNow();

        producerExecutor.close();
    }

    private ProducerRuntime submitGroupProducer(
            TransferGroup group,
            FolderTransferProducer producer) {

        if (group == null) {
            throw new IllegalArgumentException(
                    "Transfer group must not be null");
        }

        if (producer == null) {
            throw new IllegalArgumentException(
                    "Producer must not be null");
        }

        ProducerRuntime runtime =
                producerExecutor.submit(
                        producer);

        groupProducers.put(
                group.getId(),
                runtime);

        return runtime;
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
            String repositoryId,
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
                            repositoryId,
                            bucket,
                            prefix,
                            sourceRefreshRequired);
                });
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

        /*
         * File-level grouped task'larda discovery producer
         * olmadığı için detected sayısını burada artırıyoruz.
         *
         * Folder producer'lar kendi discovery lifecycle'larında
         * detected() çağrısını zaten yapıyor.
         */
        group.detected(
                Math.max(
                        0L,
                        task.getSize()));

        queue.add(task);
    }

    public TransferGroup createOperationGroup(
            TransferType operation,
            String displayName,
            String sourceRepositoryId,
            String sourceBucket,
            String sourcePrefix,
            String targetRepositoryId,
            String targetBucket,
            String targetPrefix) {

        if (displayName == null
                || displayName.isBlank()) {

            displayName = sourcePrefix;
        }

        String source =
                buildGroupLocation(
                        sourceRepositoryId,
                        sourceBucket,
                        sourcePrefix);

        String target =
                buildGroupLocation(
                        targetRepositoryId,
                        targetBucket,
                        targetPrefix);

        return new TransferGroup(
                UUID.randomUUID(),
                displayName,
                operation,
                source,
                target,
                sourceRepositoryId,
                sourceBucket,
                sourcePrefix,
                targetRepositoryId,
                targetBucket,
                targetPrefix);
    }

    private TransferGroup createObjectOperationGroup(
            TransferType operation,
            String sourceRepository,
            String sourceBucket,
            String sourceKey,
            String targetRepository,
            String targetBucket,
            String targetKey) {

        String displayName =
                S3Util.extractFileName(sourceKey);

        if (displayName == null
                || displayName.isBlank()) {

            displayName = sourceKey;
        }

        String source =
                buildGroupLocation(
                        sourceRepository,
                        sourceBucket,
                        sourceKey);

        String target =
                buildGroupLocation(
                        targetRepository,
                        targetBucket,
                        targetKey);

        return new TransferGroup(
                UUID.randomUUID(),
                displayName,
                operation,
                source,
                target,
                sourceRepository,
                sourceBucket,
                sourceKey,
                targetRepository,
                targetBucket,
                targetKey);
    }

    private TransferGroup createFolderOperationGroup(
            TransferType operation,
            String sourceRepository,
            String sourceBucket,
            String sourcePrefix,
            String targetRepository,
            String targetBucket,
            String targetPrefix) {

        String displayName =
                S3Util.extractFolderName(
                        sourcePrefix);

        if (displayName == null
                || displayName.isBlank()) {

            displayName = sourcePrefix;
        }

        return new TransferGroup(
                UUID.randomUUID(),
                displayName,
                operation,
                sourcePrefix,
                targetPrefix,
                sourceRepository,
                sourceBucket,
                sourcePrefix,
                targetRepository,
                targetBucket,
                targetPrefix,
                true);
    }

    private String buildGroupLocation(
            String repositoryId,
            String bucket,
            String prefix) {

        StringBuilder value =
                new StringBuilder();

        if (repositoryId != null
                && !repositoryId.isBlank()) {

            value.append(repositoryId);
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
}