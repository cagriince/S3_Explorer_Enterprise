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

        System.out.println(
                "[EVENT BUS] publishGroupCompleted " +
                        "listeners=" + listeners.size() +
                        " group=" + group.getDisplayName());

        TransferGroupCompletedEvent event =
                new TransferGroupCompletedEvent(
                        group,
                        repository,
                        bucket,
                        prefix);

        for (TransferListener listener :
                listeners) {

            System.out.println(
                    "[EVENT BUS] notifying " +
                            listener.getClass().getName());

            listener.onTransferGroupCompleted(
                    event);
        }
    }
}