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
import com.company.s3explorer.transfer.worker.TransferWorker;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import com.company.s3explorer.util.S3Util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class TransferManager {
    private final TransferQueue queue;
    private final TransferContext transferContext;
    private final TransferOperationFactory operationFactory;
    private final ProducerExecutor producerExecutor;

    public TransferManager(
            S3ClientManager clientManager,
            TransferQueue queue,
            TransferEventBus eventBus,
            ProducerExecutor producerExecutor) {

        this.queue = queue;
        this.producerExecutor = producerExecutor;

        transferContext = new TransferContext(clientManager, eventBus);
        operationFactory = new TransferOperationFactory();
    }

    public boolean cancel(UUID taskId) {
        return queue.cancel(taskId);
    }

    public void cancelAll() {
        queue.cancelAll();
    }

    public void submitUpload(String repositoryName, String bucket, String key, Path localFile, long size) {
        submit(TransferTask.upload()
                .targetRepositoryName(repositoryName)
                .targetBucket(bucket)
                .targetObjectKey(key)
                .localPath(localFile)
                .size(size)
                .affectsObjectList(true)
                .affectsFolderTree(false)
                .build()
        );
    }

    public void submitDownload(String repositoryName, String bucket, String key, Path localFile, long size) {
        Path target = localFile.resolve(S3Util.extractFileName(key));
        submit(TransferTask.download()
                .repositoryName(repositoryName)
                .bucket(bucket)
                .objectKey(key)
                .localPath(target)
                .size(size)
                .affectsObjectList(false)
                .affectsFolderTree(false)
                .build()
        );
    }

    public void submitDelete(String repositoryName, String bucket, String key, long size) {
        submit(TransferTask.delete()
                .repositoryName(repositoryName)
                .bucket(bucket)
                .objectKey(key)
                .size(size)
                .affectsObjectList(true)
                .affectsFolderTree(false)
                .build()
        );
    }

    public void submitCopy(String repositoryName, String bucket, String keySource, String targetRepositoryName, String targetBucket, String keyTarget, long size) {
        submit(TransferTask.copy()
                .repositoryName(repositoryName)
                .bucket(bucket)
                .objectKey(keySource)
                .targetRepositoryName(targetRepositoryName)
                .targetBucket(targetBucket)
                .targetObjectKey(keyTarget)
                .size(size)
                .affectsObjectList(true)
                .affectsFolderTree(false)
                .build()
        );
    }

    public void submitMove(String repositoryName, String bucket, String keySource, String targetRepositoryName, String targetBucket, String keyTarget, long size) {
        submit(TransferTask.move()
                .repositoryName(repositoryName)
                .bucket(bucket)
                .objectKey(keySource)
                .targetRepositoryName(targetRepositoryName)
                .targetBucket(targetBucket)
                .targetObjectKey(keyTarget)
                .size(size)
                .affectsObjectList(true)
                .affectsFolderTree(true)
                .build()
        );
    }

    public void submitCreateFolder(String repositoryName, String bucket, String key, String prefix) {
        submit(TransferTask.createFolder()
                .repositoryName(repositoryName)
                .bucket(bucket)
                .objectKey(key)
                .addRefreshPrefix(new RefreshTreeNode(prefix, RefreshTreeOperation.ADD))
                .size(0)
                .affectsObjectList(true)
                .affectsFolderTree(true)
                .build()
        );
    }

    public void submitFolderDelete(String repositoryName, String bucket, String key) {
        producerExecutor.submit(
            new FolderDeleteProducer(
                    transferContext,
                    queue,
                    repositoryName,
                    bucket,
                    key)
        );
    }

    public void submitFolderDownload(String repositoryName, String bucket, String prefix, Path localFolder) {
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

    public void submitFolderUpload(String repositoryName, String bucket, String targetPrefix, Path folder) throws IOException {
        producerExecutor.submit(
            new FolderUploadProducer(
                    queue,
                    repositoryName,
                    bucket,
                    targetPrefix,
                    folder)
        );
/*
        String groupName = folder.getFileName().toString();
        TransferGroup group = new TransferGroup(UUID.randomUUID(), groupName);
        String targetChildPrefix = targetPrefix + groupName + "/";

        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relative = folder.relativize(file).toString().replace("\\", "/");
                        String key = S3Util.combineKey(targetChildPrefix, relative);

                        submit(
                                TransferTask.upload()
                                        .targetRepositoryName(repositoryName)
                                        .targetBucket(bucket)
                                        .targetObjectKey(key)
                                        .localPath(file)
                                        .addRefreshPrefix(new RefreshTreeNode(targetChildPrefix, RefreshTreeOperation.ADD))
                                        .size(file.toFile().length())
                                        .affectsObjectList(true)
                                        .affectsFolderTree(true)
                                        .group(group)
                                        .build());
                    });
        }*/
    }

    public void submitFolderCopy(String repositoryName, String sourceBucket, String sourcePrefix, String targetRepositoryName, String targetBucket, String targetPrefix) {
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

    public void submitFolderMove(String repositoryName, String sourceBucket, String sourcePrefix, String targetRepositoryName, String targetBucket, String targetPrefix) {
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

    private void submit(TransferTask task) {
        queue.add(task);
    }

    private TransferGroup createGroup(UUID id, String name) {
        return new TransferGroup(id, name);
    }

    private S3ExplorerService getService(String repositoryName) {
        return transferContext.getService(repositoryName);
    }
}