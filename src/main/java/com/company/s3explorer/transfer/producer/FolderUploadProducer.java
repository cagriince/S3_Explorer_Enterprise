package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import com.company.s3explorer.util.S3Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FolderUploadProducer
        implements FolderTransferProducer {

    private final TransferQueue queue;

    private final String repository;
    private final String bucket;
    private final String targetPrefix;

    private final Path folder;

    private final TransferGroup group;

    public FolderUploadProducer(
            TransferQueue queue,
            String repository,
            String bucket,
            String targetPrefix,
            Path folder) {

        this.queue = queue;
        this.repository = repository;
        this.bucket = bucket;
        this.targetPrefix = targetPrefix;
        this.folder = folder;

        this.group =
                new TransferGroup(
                        UUID.randomUUID(),
                        folder.getFileName().toString());
    }

    @Override
    public String getDescription() {
        return "Preparing folder upload...";
    }

    @Override
    public void produce(
            ProducerRuntime runtime)
            throws IOException {

        try (var paths = Files.walk(folder)) {

            paths.filter(Files::isRegularFile)
                    .forEach(file -> {

                        if (runtime.isInterruptedOrCancelRequested()) {
                            throw new ProducerCancelledException();
                        }

                        produceFile(file);

                        runtime.incrementDiscovered();
                    });
        }
    }

    private void produceFile(Path file) {

        String relative =
                folder.relativize(file)
                        .toString()
                        .replace("\\", "/");

        String targetChildPrefix =
                targetPrefix
                        + group.getDisplayName()
                        + "/";

        String key =
                S3Util.combineKey(
                        targetChildPrefix,
                        relative);

        TransferTask task =
                TransferTask.upload()
                        .targetRepositoryName(repository)
                        .targetBucket(bucket)
                        .targetObjectKey(key)
                        .localPath(file)
                        .size(getFileSize(file))
                        .affectsObjectList(true)
                        .affectsFolderTree(true)
                        .addRefreshPrefix(
                                new RefreshTreeNode(
                                        targetChildPrefix,
                                        RefreshTreeOperation.ADD))
                        .group(group)
                        .build();

        queue.add(task);
    }

    private long getFileSize(Path file) {

        try {
            return Files.size(file);

        } catch (IOException ex) {

            throw new RuntimeException(
                    "File size cannot be read: "
                            + file,
                    ex);
        }
    }
}