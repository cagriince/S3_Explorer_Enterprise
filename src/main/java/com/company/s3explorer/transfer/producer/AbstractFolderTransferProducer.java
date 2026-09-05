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

    /*
     * Group UI update throttling.
     *
     * Büyük klasörlerde her object için Swing event
     * üretmek EDT'yi gereksiz yere dolduruyordu.
     *
     * Producer tarafında discovery devam ederken
     * UI'ya en fazla yaklaşık 100 ms'de bir update
     * gönderiyoruz.
     */
    private static final long GROUP_UPDATE_INTERVAL_MS = 100L;

    private volatile long lastGroupUpdateTime;

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

        /*
         * Producer'ın gerçekten başladığını UI'ya
         * hemen bildir.
         */
        publishGroupUpdatedNow();

        try {

            context.getService(repository)
                    .forEachObject(
                            bucket,
                            prefix,
                            object -> produceObject(
                                    object,
                                    runtime));

            /*
             * Discovery normal şekilde tamamlandı.
             */
            group.markProductionCompleted();

            /*
             * Son discovery state'i mutlaka gönder.
             */
            publishGroupUpdatedNow();

        } catch (RuntimeException ex) {

            /*
             * Cancellation bir production failure değildir.
             */
            if (runtime.isCancelRequested()
                    || Thread.currentThread().isInterrupted()) {

                group.markProductionCompleted();

            } else {

                group.markProductionFailed();
            }

            /*
             * Failure/cancellation sonrası son state
             * mutlaka UI'ya gönderilmeli.
             */
            publishGroupUpdatedNow();

            throw ex;

        } finally {

            group.producerFinished();

            /*
             * producerFinished() sonrası group'un gerçekten
             * Finished olup olmadığını UI hemen görebilsin.
             */
            publishGroupUpdatedNow();
        }
    }

    /**
     * S3 object discovery callback'i.
     *
     * Her object için task oluşturulur ve queue'ya eklenir.
     * Ancak UI group event'i throttle edilir.
     */
    private void produceObject(
            S3Object object,
            ProducerRuntime runtime) {

        /*
         * Cancel All sırasında yeni object üretmeye devam
         * etme.
         *
         * forEachObject callback'i tekrar çağırsa bile
         * cancellation state'i burada kesilir.
         */
        if (runtime.isCancelRequested()
                || Thread.currentThread().isInterrupted()) {

            return;
        }

        TransferTask task =
                createTask(object);

        group.detected(object.size());

        queue.add(task);

        /*
         * Her dosyada Swing event göndermiyoruz.
         */
        publishGroupUpdatedThrottled();
    }

    /**
     * Group update'i throttle ederek yayınlar.
     */
    private void publishGroupUpdatedThrottled() {

        long now =
                System.currentTimeMillis();

        long last =
                lastGroupUpdateTime;

        if (now - last
                < GROUP_UPDATE_INTERVAL_MS) {

            return;
        }

        /*
         * Basit CAS yerine synchronized kullanıyoruz.
         * Producer tek thread olduğu için burada amaç
         * sadece zaman bilgisinin tutarlı olmasıdır.
         */
        lastGroupUpdateTime = now;

        publishGroupUpdated();
    }

    /**
     * Throttle bypass edilerek group update yayınlar.
     *
     * İlk update, son update, failure ve cancellation
     * için kullanılır.
     */
    private void publishGroupUpdatedNow() {

        lastGroupUpdateTime =
                System.currentTimeMillis();

        publishGroupUpdated();
    }

    /**
     * Ortak group update dispatch'i.
     */
    private void publishGroupUpdated() {

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

    @Override
    public void cancelBeforeStart() {

        group.producerStarted();

        group.markProductionCompleted();

        group.producerFinished();

        publishGroupUpdatedNow();
    }
}