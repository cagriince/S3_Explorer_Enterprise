package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransferEventBus {

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

    public void publishProducer(
            ProducerRuntime runtime) {

        for (TransferListener listener :
                listeners) {

            listener.onProducerUpdated(
                    runtime);
        }
    }

    public void publishGroupCompleted(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix) {

        TransferGroupCompletedEvent event =
                new TransferGroupCompletedEvent(
                        group,
                        repository,
                        bucket,
                        prefix);

        for (TransferListener listener :
                listeners) {

            listener.onTransferGroupCompleted(
                    event);
        }
    }
}