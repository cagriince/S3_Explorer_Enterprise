package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import com.company.s3explorer.util.S3Util;
import software.amazon.awssdk.services.s3.model.S3Object;

public class FolderRenameProducer
        extends AbstractCopyMoveProducer {

    public FolderRenameProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            String targetPrefix) {

        super(
                context,
                queue,
                repository,
                bucket,
                prefix,
                repository,
                bucket,
                targetPrefix,
                true);
    }

    @Override
    public String getDescription() {
        return "Renaming folder...";
    }

    @Override
    protected TransferTask createTask(
            S3Object object) {

        String targetKey =
                buildRenameTargetKey(object);

        return TransferTask.move()
                .repositoryName(repository)
                .bucket(bucket)
                .objectKey(object.key())
                .targetRepositoryName(targetRepository)
                .targetBucket(targetBucket)
                .targetObjectKey(targetKey)
                .group(group)
                .size(object.size())
                .affectsObjectList(true)
                .affectsFolderTree(true)
                .addRefreshPrefix(
                        new RefreshTreeNode(
                                prefix,
                                RefreshTreeOperation.DELETE))
                .addRefreshPrefix(
                        new RefreshTreeNode(
                                targetPrefix,
                                RefreshTreeOperation.ADD))
                .build();
    }

    private String buildRenameTargetKey(
            S3Object object) {

        String relative =
                object.key()
                        .substring(
                                prefix.length());

        return S3Util.combineKey(
                targetPrefix,
                relative);
    }
}