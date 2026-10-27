package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.event.TransferEventBus;

import java.util.Map;
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
            ProducerHandle> runningProducers =
            new ConcurrentHashMap<>();

    /*
     * submit() ile cancel()/cancelAll() arasındaki
     * lifecycle yarışını kontrol eder.
     */
    private final Object lifecycleLock =
            new Object();

    private static class ProducerHandle {

        private final FolderTransferProducer producer;
        private final Future<?> future;

        private ProducerHandle(
                FolderTransferProducer producer,
                Future<?> future) {

            this.producer = producer;
            this.future = future;
        }
    }

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
                new ProducerRuntime();

        runtime.setProgressCallback(
                () -> eventBus.publishProducer(runtime));

        synchronized (lifecycleLock) {

            Future<?> future =
                    executor.submit(() ->
                            runProducer(
                                    producer,
                                    runtime));

            /*
             * Future oluşturulduktan hemen sonra runtime
             * mutlaka map'e eklenir.
             *
             * cancel() / cancelAll() lifecycleLock kullandığı
             * için burada yarış oluşamaz.
             */
            runningProducers.put(
                    runtime,
                    new ProducerHandle(
                            producer,
                            future));
        }

        return runtime;
    }

    public boolean cancel(
            ProducerRuntime runtime) {

        if (runtime == null) {
            return false;
        }

        synchronized (lifecycleLock) {

            ProducerHandle handle =
                    runningProducers.get(runtime);

            if (handle == null) {
                return false;
            }

            /*
             * Önce mantıksal cancellation.
             */
            runtime.requestCancel();

            runtime.forceNextUiPublish();

            eventBus.publishProducer(runtime);

            /*
             * Producer thread'i henüz başlamadıysa
             * group lifecycle'ını burada kapat.
             */
            if (runtime.tryCancelBeforeStart()) {

                handle.producer
                        .cancelBeforeStart();
            }

            /*
             * Producer çalışıyorsa interrupt edilir.
             * Çalışmıyorsa Future CANCELLED olur ve
             * runProducer() execution'a giremez.
             */
            handle.future.cancel(true);

            return true;
        }
    }

    public void cancelAll() {

        synchronized (lifecycleLock) {

            for (Map.Entry<
                    ProducerRuntime,
                    ProducerHandle> entry
                    : runningProducers.entrySet()) {

                ProducerRuntime runtime =
                        entry.getKey();

                ProducerHandle handle =
                        entry.getValue();

                if (runtime == null
                        || handle == null) {

                    continue;
                }

                /*
                 * Önce mantıksal cancellation.
                 */
                runtime.requestCancel();

                runtime.forceNextUiPublish();

                eventBus.publishProducer(runtime);

                /*
                 * Producer henüz execution'a başlamadıysa
                 * group lifecycle'ını burada kapat.
                 *
                 * Eğer runProducer() önce başladıysa
                 * tryCancelBeforeStart() false döner ve
                 * producer kendi finally lifecycle'ını yönetir.
                 */
                if (runtime.tryCancelBeforeStart()) {

                    handle.producer
                            .cancelBeforeStart();
                }

                /*
                 * Çalışan thread'i interrupt et.
                 * Henüz başlamadıysa Future CANCELLED olur.
                 */
                handle.future.cancel(true);
            }
        }
    }

    private void runProducer(
            FolderTransferProducer producer,
            ProducerRuntime runtime) {

        /*
         * Cancel All producer başlamadan önce lifecycle'ı
         * kapattıysa bu producer kesinlikle çalışmamalı.
         */
        if (!runtime.tryStartExecution()) {

            runningProducers.remove(runtime);

            return;
        }

        eventBus.publishProducer(runtime);

        try {
            producer.produce(runtime);
        }
        catch (Exception ex) {
        }
        finally {
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