package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.security.EncryptionConfig;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class BulkDownloadProducer
        implements FolderTransferProducer {

    private static final long GROUP_UPDATE_INTERVAL_MS = 100L;

    private final TransferContext context;
    private final TransferQueue queue;
    private final String repository;
    private final String bucket;
    private final List<String> objectKeys;
    private final Path localFolder;
    private final EncryptionConfig encryptionConfig;
    private final TransferGroup group;
    private final String sourcePrefix;

    private volatile long lastGroupUpdateTime;

    public BulkDownloadProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            List<String> objectKeys,
            Path localFolder,
            EncryptionConfig encryptionConfig,
            TransferGroup group,
            String sourcePrefix) {

        this.context = context;
        this.queue = queue;
        this.repository = repository;
        this.bucket = bucket;
        this.objectKeys = objectKeys;
        this.localFolder = localFolder;
        this.encryptionConfig = encryptionConfig;
        this.group = group;
        this.sourcePrefix = sourcePrefix;
    }

    @Override
    public String getDescription() {
        return group.getDisplayName();
    }

    @Override
    public void produce(
            ProducerRuntime runtime)
            throws IOException {

        group.producerStarted();

        publishGroupUpdatedNow();

        try {
            for (String objectKey : objectKeys) {

                if (runtime.isCancelRequested()
                        || Thread.currentThread().isInterrupted()) {

                    throw new CancellationException(
                            "Bulk download cancelled");
                }

                if (objectKey == null
                        || objectKey.isBlank()) {

                    continue;
                }

                if (S3Util.isFolder(objectKey)) {
                    produceFolder(
                            objectKey,
                            runtime);
                } else {
                    produceFile(objectKey);
                }
            }

            group.markProductionCompleted();

            publishGroupUpdatedNow();

        } catch (RuntimeException ex) {

            if (runtime.isCancelRequested()
                    || Thread.currentThread().isInterrupted()
                    || ex instanceof CancellationException) {

                group.markProductionCompleted();

            } else {

                group.markProductionFailed();
            }

            publishGroupUpdatedNow();

            throw ex;

        } finally {

            group.producerFinished();

            publishGroupUpdatedNow();
        }
    }

    private void produceFile(
            String objectKey) {

        HeadObjectResponse head =
                context.getService(repository)
                        .getObject(
                                bucket,
                                objectKey);

        if (head == null) {
            throw new IllegalArgumentException(
                    "S3 object not found: "
                            + objectKey);
        }

        Path target =
                localFolder.resolve(
                        S3Util.extractFileName(objectKey));

        TransferTask task =
                createTask(
                        objectKey,
                        target,
                        head.contentLength());

        group.detected(
                Math.max(
                        0L,
                        head.contentLength()));

        queue.add(task);

        publishGroupUpdatedThrottled();
    }

    private void produceFolder(
            String prefix,
            ProducerRuntime runtime) {

        context.getService(repository)
                .forEachObject(
                        bucket,
                        prefix,
                        object -> produceFolderObject(
                                prefix,
                                object,
                                runtime),
                        () ->
                                !runtime.isCancelRequested()
                                        && !Thread.currentThread()
                                        .isInterrupted());
    }

    private void produceFolderObject(
            String prefix,
            S3Object object,
            ProducerRuntime runtime) {

        if (runtime.isCancelRequested()
                || Thread.currentThread().isInterrupted()) {

            throw new CancellationException(
                    "Bulk download cancelled");
        }

        if (object.key().equals(prefix)
                || S3Util.isFolder(object.key())) {

            return;
        }

        String relativePath =
                object.key()
                        .substring(prefix.length());

        Path target =
                localFolder
                        .resolve(
                                S3Util.extractFolderName(prefix))
                        .resolve(relativePath);

        TransferTask task =
                createTask(
                        object.key(),
                        target,
                        object.size());

        group.detected(
                Math.max(
                        0L,
                        object.size()));

        queue.add(task);

        publishGroupUpdatedThrottled();
    }

    private TransferTask createTask(
            String objectKey,
            Path target,
            long size) {

        TransferTask.Builder builder =
                TransferTask.download()
                        .repositoryName(repository)
                        .bucket(bucket)
                        .objectKey(objectKey)
                        .localPath(target)
                        .size(size)
                        .affectsObjectList(false)
                        .affectsFolderTree(false)
                        .group(group);

        if (encryptionConfig != null) {
            builder.encryptionConfig(
                    encryptionConfig);
        }

        return builder.build();
    }

    private void publishGroupUpdatedThrottled() {

        long now =
                System.currentTimeMillis();

        if (now - lastGroupUpdateTime
                < GROUP_UPDATE_INTERVAL_MS) {

            return;
        }

        lastGroupUpdateTime = now;

        publishGroupUpdated();
    }

    private void publishGroupUpdatedNow() {

        lastGroupUpdateTime =
                System.currentTimeMillis();

        publishGroupUpdated();
    }

    private void publishGroupUpdated() {

        context.publishGroupUpdated(
                group,
                repository,
                bucket,
                sourcePrefix,
                false);
    }

    @Override
    public void cancelBeforeStart() {

        group.producerStarted();

        group.markProductionCompleted();

        group.producerFinished();

        publishGroupUpdatedNow();
    }
}