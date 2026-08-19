package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.UUID;

public abstract class AbstractFolderTransferProducer
        implements FolderTransferProducer {

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

        this.group = new TransferGroup(
                UUID.randomUUID(),
                S3Util.extractFolderName(prefix));

        this.group.setCompletionCallback(
                () -> {

                    System.out.println(
                            "[GROUP CALLBACK] " +
                                    "group=" + this.group.getDisplayName() +
                                    " finished=" + this.group.isFinished() +
                                    " successful=" + this.group.isFullySuccessful() +
                                    " queued=" + this.group.getQueued() +
                                    " running=" + this.group.getRunning() +
                                    " completed=" + this.group.getCompleted() +
                                    " failed=" + this.group.getFailed() +
                                    " cancelled=" + this.group.getCancelled());

                    if (this.group.isFullySuccessful()) {

                        System.out.println(
                                "[GROUP CALLBACK] publishing group completed");

                        context.publishGroupCompleted(
                                this.group,
                                repository,
                                bucket,
                                prefix);
                    }
                });
    }

    @Override
    public final void produce(
            ProducerRuntime runtime) {

        try {

            context.getService(repository)
                    .forEachObject(
                            bucket,
                            prefix,
                            object -> {

                                if (runtime.isInterruptedOrCancelRequested()) {
                                    throw new ProducerCancelledException();
                                }

                                TransferTask task =
                                        createTask(object);

                                queue.add(task);

                                runtime.incrementDiscovered();

                                if (runtime.shouldPublishUi(100)) {
                                    context.publishProducer(runtime);
                                }
                            });

        } finally {

            /*
             * Producer artık yeni task üretmeyecek.
             *
             * Eğer cancellation nedeniyle çıkıldıysa da
             * bu bilgi yine verilmelidir.
             */
            group.markProductionCompleted();

            runtime.forceNextUiPublish();

            context.publishProducer(runtime);
        }
    }
    
    protected abstract TransferTask createTask(
            S3Object object);
}