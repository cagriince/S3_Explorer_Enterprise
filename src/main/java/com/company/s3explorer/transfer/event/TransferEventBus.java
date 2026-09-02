package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.producer.ProducerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransferEventBus {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TransferEventBus.class);

    private final List<TransferListener> listeners =
            new CopyOnWriteArrayList<>();

    public void subscribe(
            TransferListener listener) {

        listeners.add(listener);
    }

    public void publish(
            TransferRuntime runtime) {

        for (TransferListener listener :
                listeners) {

            listener.onTransferUpdated(
                    runtime);
        }
    }

    public void publishBatch(
            List<TransferRuntime> runtimes) {

        if (runtimes == null
                || runtimes.isEmpty()) {
            return;
        }

        for (TransferListener listener :
                listeners) {

            listener.onTransfersUpdated(
                    runtimes);
        }
    }

    public void publishProducer(
            ProducerRuntime runtime) {

        for (TransferListener listener :
                listeners) {

            listener.onProducerUpdated(
                    runtime);
        }
    }

    /**
     * Publishes a group completion event while
     * explicitly describing whether the source side
     * requires a refresh.
     *
     * This prevents COPY operations from being
     * interpreted as source DELETE operations.
     */
    public void publishGroupCompleted(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix,
            boolean sourceRefreshRequired) {

        TransferGroupCompletedEvent event =
                new TransferGroupCompletedEvent(
                        group,
                        repository,
                        bucket,
                        prefix,
                        sourceRefreshRequired);

        log.debug(
                "[EVENT BUS] publishGroupCompleted " +
                        "listeners={} group={} " +
                        "sourceRefreshRequired={}",
                listeners.size(),
                group.getDisplayName(),
                sourceRefreshRequired);

        for (TransferListener listener :
                listeners) {

            log.debug(
                    "[EVENT BUS] notifying {}",
                    listener.getClass().getName());

            listener.onTransferGroupCompleted(
                    event);
        }
    }

    /**
     * Backward-compatible publication path.
     *
     * Existing folder producers continue to use
     * this method until their source-refresh policy
     * is explicitly supplied.
     */
    public void publishGroupCompleted(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix) {

        publishGroupCompleted(
                group,
                repository,
                bucket,
                prefix,
                true);
    }
}