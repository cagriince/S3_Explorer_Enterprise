package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import software.amazon.awssdk.services.s3.model.S3Object;

public class FolderMoveProducer
        extends AbstractCopyMoveProducer {

    public FolderMoveProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            String targetRepository,
            String targetBucket,
            String targetPrefix) {

        super(
                context,
                queue,
                repository,
                bucket,
                prefix,
                targetRepository,
                targetBucket,
                targetPrefix);
    }

    @Override
    public String getDescription() {
        return "Preparing folder move...";
    }

    @Override
    protected TransferTask createTask(
            S3Object object) {

        return TransferTask.move()
                .repositoryName(repository)
                .bucket(bucket)
                .objectKey(object.key())
                .targetRepositoryName(targetRepository)
                .targetBucket(targetBucket)
                .targetObjectKey(
                        buildTargetKey(object))
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
                                getTargetChildPrefix(),
                                RefreshTreeOperation.ADD))
                .build();
    }
}