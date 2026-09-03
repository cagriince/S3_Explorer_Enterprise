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

    private final boolean ownsGroupLifecycle;
    private final boolean sourceRefreshRequired;

    /**
     * Backward-compatible constructor.
     *
     * Existing folder producers use this constructor.
     * Source refresh remains enabled for these legacy
     * folder operations.
     */
    protected AbstractFolderTransferProducer(
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
                null,
                true);
    }

    /**
     * Constructor for folder operations that explicitly
     * define their source refresh behavior.
     */
    protected AbstractFolderTransferProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            boolean sourceRefreshRequired) {

        this(
                context,
                queue,
                repository,
                bucket,
                prefix,
                null,
                sourceRefreshRequired);
    }

    /**
     * Constructor for a producer using an external/shared
     * TransferGroup.
     */
    protected AbstractFolderTransferProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            TransferGroup externalGroup,
            boolean sourceRefreshRequired) {

        this.context = context;
        this.queue = queue;

        this.repository = repository;
        this.bucket = bucket;
        this.prefix = prefix;

        this.sourceRefreshRequired =
                sourceRefreshRequired;

        if (externalGroup == null) {

            this.group = new TransferGroup(
                    UUID.randomUUID(),
                    S3Util.extractFolderName(prefix));

            this.ownsGroupLifecycle = true;

        } else {

            this.group = externalGroup;

            this.ownsGroupLifecycle = false;
        }

        this.group.registerProducer();

        if (ownsGroupLifecycle) {
            configureOwnGroupCompletion();
        }
    }

    private void configureOwnGroupCompletion() {

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

                    if (this.group.isFinished()) {

                        System.out.println(
                                "[GROUP CALLBACK] publishing group completed");

                        context.publishGroupCompleted(
                                this.group,
                                repository,
                                bucket,
                                prefix,
                                sourceRefreshRequired);
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

        }
        finally {

            /*
             * Every producer releases its producer registration.
             *
             * TransferGroup changes productionCompleted to true
             * only when the last registered producer finishes.
             */
            group.producerCompleted();

            runtime.forceNextUiPublish();

            context.publishProducer(runtime);
        }
    }

    protected abstract TransferTask createTask(
            S3Object object);
}