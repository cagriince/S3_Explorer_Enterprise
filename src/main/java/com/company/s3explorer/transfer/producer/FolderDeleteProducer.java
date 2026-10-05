package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;

import software.amazon.awssdk.services.s3.model.S3Object;

public class FolderDeleteProducer
        extends AbstractFolderTransferProducer {

    public FolderDeleteProducer(
            TransferContext context,
            TransferQueue queue,
            String repositoryId,
            String bucket,
            String prefix,
            TransferGroup group) {

        super(
                context,
                queue,
                repositoryId,
                bucket,
                prefix,
                group);
    }

    @Override
    public String getDescription() {
        return "Preparing folder delete...";
    }

    @Override
    protected TransferTask createTask(
            S3Object object) {

        return TransferTask.delete()
                .repositoryId(repositoryId)
                .bucket(bucket)
                .objectKey(object.key())
                .size(object.size())
                .affectsObjectList(true)
                .affectsFolderTree(false)
                .group(group)
                .build();
    }
}