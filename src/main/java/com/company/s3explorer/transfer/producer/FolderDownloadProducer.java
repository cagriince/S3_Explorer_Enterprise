package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.security.EncryptionConfig;
import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.file.Path;

public class FolderDownloadProducer
        extends AbstractFolderTransferProducer {

    private final Path localFolder;
    private final EncryptionConfig encryptionConfig;

    public FolderDownloadProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            Path localFolder) {

        this(
                context,
                queue,
                repository,
                bucket,
                prefix,
                localFolder,
                null);
    }

    public FolderDownloadProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            Path localFolder,
            EncryptionConfig encryptionConfig) {

        super(
                context,
                queue,
                repository,
                bucket,
                prefix);

        this.localFolder = localFolder;
        this.encryptionConfig = encryptionConfig;
    }

    @Override
    public String getDescription() {
        return "Preparing folder download...";
    }

    @Override
    protected TransferTask createTask(S3Object object) {

        Path target =
                localFolder.resolve(
                        group.getDisplayName()
                                + "/"
                                + object.key()
                                .substring(prefix.length()));

        return TransferTask.download()
                .repositoryName(repository)
                .bucket(bucket)
                .objectKey(object.key())
                .localPath(target)
                .size(object.size())
                .encryptionConfig(encryptionConfig)
                .affectsObjectList(false)
                .affectsFolderTree(false)
                .group(group)
                .build();
    }
}