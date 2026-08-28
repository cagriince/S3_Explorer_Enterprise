package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.manager.TransferManager;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

public final class ExplorerFileOperationController {

    private final TransferManager transferManager;
    private final Supplier<String> currentRepositorySupplier;
    private final Supplier<String> currentBucketSupplier;

    public ExplorerFileOperationController(
            TransferManager transferManager,
            Supplier<String> currentRepositorySupplier,
            Supplier<String> currentBucketSupplier) {

        this.transferManager =
                Objects.requireNonNull(transferManager);

        this.currentRepositorySupplier =
                Objects.requireNonNull(
                        currentRepositorySupplier);

        this.currentBucketSupplier =
                Objects.requireNonNull(
                        currentBucketSupplier);
    }

    public void delete(S3FileItem item) {

        if (item == null || item.isParentFolder()) {
            return;
        }

        String bucket =
                currentBucketSupplier.get();

        if (bucket == null) {
            return;
        }

        if (item.isFolder()) {

            transferManager.submitFolderDelete(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey());

        } else {

            transferManager.submitDelete(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey(),
                    item.getSize());
        }
    }

    public void download(
            S3FileItem item,
            Path destination) {

        if (item == null
                || item.isParentFolder()
                || destination == null) {
            return;
        }

        String bucket =
                currentBucketSupplier.get();

        if (bucket == null) {
            return;
        }

        if (item.isFolder()) {

            transferManager.submitFolderDownload(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey(),
                    destination);

        } else {

            transferManager.submitDownload(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey(),
                    destination,
                    item.getSize());
        }
    }

    public void copy(
            S3FileItem item,
            String targetBucket,
            String targetKey) {

        if (item == null
                || targetBucket == null
                || targetKey == null) {
            return;
        }

        String repositoryName =
                currentRepositorySupplier.get();

        if (repositoryName == null) {
            return;
        }

        if (item.isFolder()) {

            transferManager.submitFolderCopy(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey);

            return;
        }

        transferManager.submitCopy(
                item.getRepositoryName(),
                item.getBucket(),
                item.getKey(),
                repositoryName,
                targetBucket,
                targetKey,
                item.getSize());
    }

    public void move(
            S3FileItem item,
            String targetBucket,
            String targetKey) {

        if (item == null
                || targetBucket == null
                || targetKey == null) {
            return;
        }

        String repositoryName =
                currentRepositorySupplier.get();

        if (repositoryName == null) {
            return;
        }

        if (item.isFolder()) {

            transferManager.submitFolderMove(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey);

            return;
        }

        transferManager.submitMove(
                item.getRepositoryName(),
                item.getBucket(),
                item.getKey(),
                repositoryName,
                targetBucket,
                targetKey,
                item.getSize());
    }
}