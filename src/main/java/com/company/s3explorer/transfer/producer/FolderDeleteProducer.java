package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.RefreshTreeNode;
import com.company.s3explorer.ui.explorer.RefreshTreeOperation;
import software.amazon.awssdk.services.s3.model.S3Object;

public class FolderDeleteProducer extends AbstractFolderTransferProducer {

    public FolderDeleteProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix) {

        super(
                context,
                queue,
                repository,
                bucket,
                prefix);
    }

    @Override
    protected TransferTask createTask(S3Object object) {
        return TransferTask.delete()
                        .repositoryName(repository)
                        .bucket(bucket)
                        .objectKey(object.key())
                        .addRefreshPrefix(new RefreshTreeNode(prefix, RefreshTreeOperation.DELETE))
                        .size(object.size())
                        .affectsObjectList(false)
                        .affectsFolderTree(true)
                        .group(group)
                        .build();
    }
}
