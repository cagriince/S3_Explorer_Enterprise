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
            String repository,
            String bucket,
            String prefix) {

        this(
                context,
                queue,
                repository,
                bucket,
                prefix,
                null);
    }

    public FolderDeleteProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            TransferGroup group) {

        super(
                context,
                queue,
                repository,
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
                .repositoryName(repository)
                .bucket(bucket)
                .objectKey(object.key())
                .size(object.size())
                .affectsObjectList(true)
                .affectsFolderTree(false)
                .group(group)
                .build();
    }
}