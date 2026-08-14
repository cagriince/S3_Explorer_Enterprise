package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProducerExecutor
        implements AutoCloseable {

    private final ExecutorService executor;
    private final TransferEventBus eventBus;

    private final Map<
            ProducerRuntime,
            Future<?>> runningProducers =
            new ConcurrentHashMap<>();

    public ProducerExecutor(
            TransferEventBus eventBus) {

        this.eventBus = eventBus;

        this.executor =
                Executors.newSingleThreadExecutor(r -> {

                    Thread thread =
                            new Thread(
                                    r,
                                    "FolderTransferProducer");

                    thread.setDaemon(true);

                    return thread;
                });
    }

    public ProducerRuntime submit(
            FolderTransferProducer producer) {

        ProducerRuntime runtime =
                new ProducerRuntime(
                        producer.getDescription());

        runtime.setProgressCallback(
                () -> eventBus.publishProducer(runtime));

        Future<?> future =
                executor.submit(() ->
                        runProducer(
                                producer,
                                runtime));

        runningProducers.put(
                runtime,
                future);

        return runtime;
    }

    public boolean cancel(
            ProducerRuntime runtime) {

        if (runtime == null) {
            return false;
        }

        runtime.requestCancel();

        Future<?> future =
                runningProducers.get(runtime);

        if (future == null) {
            return false;
        }

        future.cancel(true);

        eventBus.publishProducer(runtime);

        return true;
    }

    public void cancelAll() {

        for (Map.Entry<
                ProducerRuntime,
                Future<?>> entry
                : runningProducers.entrySet()) {

            ProducerRuntime runtime =
                    entry.getKey();

            Future<?> future =
                    entry.getValue();

            runtime.requestCancel();

            future.cancel(true);

            eventBus.publishProducer(runtime);
        }
    }

    private void runProducer(
            FolderTransferProducer producer,
            ProducerRuntime runtime) {

        runtime.setStartTime(
                Instant.now());

        runtime.setStatus(
                TransferStatus.RUNNING);

        eventBus.publishProducer(runtime);

        try {

            producer.produce(runtime);

            if (runtime.isCancelRequested()) {

                runtime.setMessage(
                        "Producer cancelled");

                runtime.setStatus(
                        TransferStatus.CANCELLED);

            } else {

                runtime.setMessage(
                        "Folder preparation completed");

                runtime.setStatus(
                        TransferStatus.COMPLETED);
            }

        } catch (ProducerCancelledException ex) {

            runtime.setMessage(
                    "Producer cancelled");

            runtime.setStatus(
                    TransferStatus.CANCELLED);

        } catch (Exception ex) {

            runtime.setMessage(
                    ex.getMessage());

            runtime.setStatus(
                    TransferStatus.FAILED);

        } finally {

            runtime.setEndTime(
                    Instant.now());

            runtime.forceNextUiPublish();

            eventBus.publishProducer(runtime);

            runningProducers.remove(runtime);
        }
    }

    @Override
    public void close() {

        cancelAll();

        executor.shutdownNow();

        runningProducers.clear();
    }
}