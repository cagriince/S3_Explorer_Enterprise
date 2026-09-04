package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.context.TransferContext;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.util.S3Util;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.util.UUID;

public abstract class AbstractFolderTransferProducer
        implements FolderTransferProducer {

    protected final TransferContext context;
    protected final TransferQueue queue;

    protected final String repository;
    protected final String bucket;
    protected final String prefix;

    protected final TransferGroup group;

    /**
     * Producer kendi group'unu oluşturur.
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
                null);
    }

    /**
     * Dışarıdan group verilmişse aynı group kullanılır.
     * Böylece TransferManager / UI tarafından oluşturulan
     * logical group ile producer aynı group üzerinde çalışır.
     */
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

        this.group =
                externalGroup != null
                        ? externalGroup
                        : new TransferGroup(
                        UUID.randomUUID(),
                        S3Util.extractFolderName(prefix));
    }

    public TransferGroup getGroup() {
        return group;
    }

    @Override
    public void produce(
            ProducerRuntime runtime)
            throws IOException {

        group.producerStarted();

        context.publishGroupUpdated(
                group,
                repository,
                bucket,
                prefix,
                "MOVE".equalsIgnoreCase(
                        group.getOperation()));

        try {

            context.getService(repository)
                    .forEachObject(
                            bucket,
                            prefix,
                            this::produceObject);

            /*
             * Producer normal şekilde tamamlandı.
             */
            group.markProductionCompleted();

            context.publishGroupUpdated(
                    group,
                    repository,
                    bucket,
                    prefix,
                    "MOVE".equalsIgnoreCase(
                            group.getOperation()));

        } catch (RuntimeException ex) {

            /*
             * Producer cancellation sırasında interrupt edilmiş
             * veya cancellation nedeniyle RuntimeException oluşmuşsa
             * group lifecycle mutlaka kapanmalı.
             *
             * Aksi halde productionCompleted=false kalır ve
             * group Finished durumuna geçemez.
             */
            group.markProductionFailed();

            context.publishGroupUpdated(
                    group,
                    repository,
                    bucket,
                    prefix,
                    "MOVE".equalsIgnoreCase(
                            group.getOperation()));

            throw ex;

        } finally {

            /*
             * Hem normal tamamlanmada hem cancellation/failure
             * durumunda producer mutlaka kapanır.
             */
            group.producerFinished();

            context.publishGroupUpdated(
                    group,
                    repository,
                    bucket,
                    prefix,
                    "MOVE".equalsIgnoreCase(
                            group.getOperation()));
        }
    }

    private void produceObject(
            S3Object object) {

        TransferTask task =
                createTask(object);

        group.detected(object.size());

        queue.add(task);

        context.publishGroupUpdated(
                group,
                repository,
                bucket,
                prefix,
                "MOVE".equalsIgnoreCase(
                        group.getOperation()));
    }
    
    @Override
    public String getDescription() {
        return group.getDisplayName();
    }

    protected abstract TransferTask createTask(
            S3Object object);
}