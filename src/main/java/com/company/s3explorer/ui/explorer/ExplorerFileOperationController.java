package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.security.EncryptionConfig;

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

    public void delete(
            S3FileItem item,
            TransferGroup group) {

        if (item == null
                || item.isParentFolder()) {
            return;
        }

        String bucket =
                currentBucketSupplier.get();

        if (bucket == null) {
            return;
        }

        if (item.isFolder()) {

            if (group == null) {

                transferManager.submitFolderDelete(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey());

            } else {

                throw new IllegalArgumentException(
                        "Grouped folder delete is not supported " +
                                "by this operation");
            }

        } else {

            if (group == null) {

                transferManager.submitDelete(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        item.getSize());

            } else {

                transferManager.submitDelete(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        item.getSize(),
                        group);
            }
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

    public void download(
            S3FileItem item,
            Path destination,
            TransferGroup group) {

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

            if (group == null) {

                transferManager.submitFolderDownload(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination);

            } else {

                throw new IllegalArgumentException(
                        "Grouped folder download is not supported " +
                                "by this operation");
            }

        } else {

            if (group == null) {

                transferManager.submitDownload(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination,
                        item.getSize());

            } else {

                transferManager.submitDownload(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination,
                        item.getSize(),
                        group);
            }
        }
    }
    
    public void downloadDecrypted(
            S3FileItem item,
            Path destination,
            EncryptionConfig encryptionConfig) {

        if (item == null
                || item.isParentFolder()
                || destination == null
                || encryptionConfig == null) {
            return;
        }

        String bucket =
                currentBucketSupplier.get();

        if (bucket == null) {
            return;
        }

        if (item.isFolder()) {

            transferManager.submitFolderDownloadDecrypted(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey(),
                    destination,
                    encryptionConfig);

        } else {

            transferManager.submitDownloadDecrypted(
                    item.getRepositoryName(),
                    bucket,
                    item.getKey(),
                    destination,
                    item.getSize(),
                    encryptionConfig);
        }
    }

    public void downloadDecrypted(
            S3FileItem item,
            Path destination,
            EncryptionConfig encryptionConfig,
            TransferGroup group) {

        if (item == null
                || item.isParentFolder()
                || destination == null
                || encryptionConfig == null) {
            return;
        }

        String bucket =
                currentBucketSupplier.get();

        if (bucket == null) {
            return;
        }

        if (item.isFolder()) {

            if (group == null) {

                transferManager.submitFolderDownloadDecrypted(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination,
                        encryptionConfig);

            } else {

                throw new IllegalArgumentException(
                        "Grouped folder download is not supported " +
                                "by this operation");
            }

        } else {

            if (group == null) {

                transferManager.submitDownloadDecrypted(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination,
                        item.getSize(),
                        encryptionConfig);

            } else {

                transferManager.submitDownloadDecrypted(
                        item.getRepositoryName(),
                        bucket,
                        item.getKey(),
                        destination,
                        item.getSize(),
                        encryptionConfig,
                        group);
            }
        }
    }
    
    public void copy(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite) {

        copy(
                item,
                targetBucket,
                targetKey,
                overwrite,
                null);
    }

    public void copy(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group) {

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

            if (group == null) {

                transferManager.submitFolderCopy(
                        item.getRepositoryName(),
                        item.getBucket(),
                        item.getKey(),
                        repositoryName,
                        targetBucket,
                        targetKey);

            } else {

                transferManager.submitFolderCopy(
                        item.getRepositoryName(),
                        item.getBucket(),
                        item.getKey(),
                        repositoryName,
                        targetBucket,
                        targetKey,
                        group);
            }

            return;
        }

        if (group == null) {

            transferManager.submitCopy(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey,
                    item.getSize(),
                    overwrite);

        } else {

            transferManager.submitCopy(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey,
                    item.getSize(),
                    overwrite,
                    group);
        }
    }

    public void move(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite) {

        move(
                item,
                targetBucket,
                targetKey,
                overwrite,
                null);
    }

    public void move(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group) {

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

            if (group == null) {

                transferManager.submitFolderMove(
                        item.getRepositoryName(),
                        item.getBucket(),
                        item.getKey(),
                        repositoryName,
                        targetBucket,
                        targetKey);

            } else {

                transferManager.submitFolderMove(
                        item.getRepositoryName(),
                        item.getBucket(),
                        item.getKey(),
                        repositoryName,
                        targetBucket,
                        targetKey,
                        group);
            }

            return;
        }

        if (group == null) {

            transferManager.submitMove(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey,
                    item.getSize(),
                    overwrite);

        } else {

            transferManager.submitMove(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    repositoryName,
                    targetBucket,
                    targetKey,
                    item.getSize(),
                    overwrite,
                    group);
        }
    }
}