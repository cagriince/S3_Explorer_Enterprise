package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
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
                true);
    }

    @Override
    public String getDescription() {
        return "Preparing folder move...";
    }

    @Override
    protected TransferTask createTask(
            S3Object object) {

        return TransferTask.move()
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
                 * Folder move is a silent merge.
                 *
                 * Existing target objects must not be
                 * overwritten.
                 *
                 * If the target object already exists,
                 * the move task fails before source deletion.
                 */
                .overwrite(false)

                .affectsObjectList(true)
                .affectsFolderTree(true)

                /*
                 * The source tree must not be removed at
                 * individual task level.
                 *
                 * Source cleanup is handled only after the
                 * complete group succeeds.
                 */
                .addRefreshPrefix(
                        new RefreshTreeNode(
                                getTargetChildPrefix(),
                                RefreshTreeOperation.ADD))
                .build();
    }
}