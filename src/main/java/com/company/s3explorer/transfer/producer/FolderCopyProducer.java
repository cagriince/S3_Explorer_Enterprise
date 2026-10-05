package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import software.amazon.awssdk.services.s3.model.S3Object;

public class FolderCopyProducer
        extends AbstractCopyMoveProducer {

    public FolderCopyProducer(
            TransferContext context,
            TransferQueue queue,
            String repositoryId,
            String bucket,
            String prefix,
            String targetRepositoryId,
            String targetBucket,
            String targetPrefix,
            TransferGroup group) {

        super(
                context,
                queue,
                repositoryId,
                bucket,
                prefix,
                targetRepositoryId,
                targetBucket,
                targetPrefix,
                group,
                false);
    }

    @Override
    public String getDescription() {
        return "Preparing folder copy...";
    }

    @Override
    protected TransferTask createTask(
            S3Object object) {

        return TransferTask.copy()
                .repositoryId(repositoryId)
                .bucket(bucket)
                .objectKey(object.key())
                .targetRepositoryId(targetRepositoryId)
                .targetBucket(targetBucket)
                .targetObjectKey(
                        buildTargetKey(object))
                .group(group)
                .size(object.size())

                /*
                 * Folder copy is a silent merge.
                 *
                 * Existing target objects must not be
                 * overwritten.
                 */
                .overwrite(false)

                .affectsObjectList(true)
                .affectsFolderTree(true)
                .addRefreshPrefix(
                        new RefreshTreeNode(
                                getTargetChildPrefix(),
                                RefreshTreeOperation.ADD))
                .build();
    }
}