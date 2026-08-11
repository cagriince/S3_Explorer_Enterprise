package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.UUID;

public abstract class AbstractFolderTransferProducer implements FolderTransferProducer {

    protected final TransferContext context;
    protected final TransferQueue queue;

    protected final String repository;
    protected final String bucket;
    protected final String prefix;

    protected final TransferGroup group;

    protected AbstractFolderTransferProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix) {

        this.context = context;
        this.queue = queue;

        this.repository = repository;
        this.bucket = bucket;
        this.prefix = prefix;

        this.group = new TransferGroup(UUID.randomUUID(), S3Util.extractFolderName(prefix));
    }

    @Override
    public final void produce() {
        context.getService(repository)
                .forEachObject(
                        bucket,
                        prefix,
                        this::produceObject);
    }

    private void produceObject(S3Object object) {
        TransferTask task = createTask(object);
        group.queued();
        queue.add(task);
    }

    protected abstract TransferTask createTask(S3Object object);
}
