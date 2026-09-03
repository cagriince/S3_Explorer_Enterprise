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

    /*
     * True when this producer owns the lifecycle of the group.
     *
     * Existing folder operations create and complete their
     * own group, so this remains true for the existing
     * constructors.
     *
     * A future shared Paste operation can provide an external
     * group and keep production lifecycle ownership outside
     * this producer.
     */
    private final boolean ownsGroupLifecycle;

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
                null);
    }

    protected AbstractFolderTransferProducer(
            TransferContext context,
            TransferQueue queue,
            String repository,
            String bucket,
            String prefix,
            TransferGroup externalGroup) {

        this.context = context;
        this.queue = queue;

        this.repository = repository;
        this.bucket = bucket;
        this.prefix = prefix;

        if (externalGroup == null) {

            this.group = new TransferGroup(
                    UUID.randomUUID(),
                    S3Util.extractFolderName(prefix));

            this.ownsGroupLifecycle = true;

            configureOwnGroupCompletion();

        }
        else {

            this.group = externalGroup;

            this.ownsGroupLifecycle = false;
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

                    /*
                     * The group completion event represents the final
                     * state of the operation, not only successful
                     * operations.
                     *
                     * Tree/source cleanup decisions must continue to
                     * use group.isFullySuccessful() where required.
                     */
                    if (this.group.isFinished()) {

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

        }
        finally {

            /*
             * A producer using its own group owns the production
             * lifecycle and must mark the group complete here.
             *
             * A shared group belongs to the caller. The caller
             * will mark production completed after all producers
             * contributing to that group have finished.
             */
            if (ownsGroupLifecycle) {
                group.markProductionCompleted();
            }

            runtime.forceNextUiPublish();

            context.publishProducer(runtime);
        }
    }

    protected abstract TransferTask createTask(
            S3Object object);
}