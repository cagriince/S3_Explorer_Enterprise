package com.company.s3explorer.transfer.queue;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TransferQueue {

    private final BlockingQueue<TransferRuntime> queue =
            new LinkedBlockingQueue<>();

    private final Map<UUID, TransferRuntime> activeTransfers =
            new ConcurrentHashMap<>();

    private final TransferEventBus eventBus;

    /*
     * Cancel All sırasında worker'ların yeni iş
     * almasını engeller.
     */
    private volatile boolean cancellingAll;

    public TransferQueue(
            TransferEventBus eventBus) {

        this.eventBus = eventBus;
    }

    public synchronized TransferRuntime add(
            TransferTask task) {

        TransferRuntime runtime =
                new TransferRuntime(task);

        /*
         * Cancel All devam ediyorsa bu task'ın
         * kuyruğa girmesine izin verme.
         *
         * Producer cancellation fark edene kadar
         * yeni task üretirse onu da doğrudan cancelled
         * yapıyoruz.
         */
        if (cancellingAll) {

            runtime.setStatus(
                    TransferStatus.CANCELLED);

            runtime.setEndTime(
                    Instant.now());

            runtime.setMessage(
                    "Transfer cancelled");

            TransferGroup group =
                    task.getGroup();

            if (group != null) {
                group.cancelled();
            }

            eventBus.publish(runtime);

            return runtime;
        }

        runtime.setStatus(
                TransferStatus.QUEUED);

        if (task.getGroup() != null) {
            task.getGroup().queued();
        }

        queue.add(runtime);

        eventBus.publish(runtime);

        return runtime;
    }

    public TransferRuntime take()
            throws InterruptedException {

        while (true) {

            if (cancellingAll) {

                Thread.sleep(50);

                continue;
            }

            TransferRuntime runtime =
                    queue.take();

            /*
             * Cancel All, take() ile aynı anda
             * yarışmış olabilir.
             *
             * Gate açıldıktan sonra burada kontrol
             * edilmesi güvenlik katmanı olarak kalıyor.
             */
            if (cancellingAll) {

                cancelRuntime(runtime);

                continue;
            }

            return runtime;
        }
    }

    public TransferRuntime poll(
            long timeoutMillis)
            throws InterruptedException {

        /*
         * Cancel All sırasında yeni iş alma.
         */
        if (cancellingAll) {

            Thread.sleep(
                    Math.min(
                            timeoutMillis,
                            50));

            return null;
        }

        TransferRuntime runtime =
                queue.poll(
                        timeoutMillis,
                        TimeUnit.MILLISECONDS);

        /*
         * poll() ile Cancel All arasında yarış
         * ihtimaline karşı ikinci kontrol.
         */
        if (runtime != null
                && cancellingAll) {

            cancelRuntime(runtime);

            return null;
        }

        return runtime;
    }

    public void markActive(
            TransferRuntime runtime) {

        activeTransfers.put(
                runtime.getTask().getId(),
                runtime);
    }

    public void markFinished(
            TransferRuntime runtime) {

        activeTransfers.remove(
                runtime.getTask().getId());
    }

    public boolean cancel(
            UUID taskId) {

        for (TransferRuntime runtime :
                queue) {

            if (!runtime.getTask()
                    .getId()
                    .equals(taskId)) {

                continue;
            }

            if (queue.remove(runtime)) {

                cancelRuntime(runtime);

                return true;
            }
        }

        TransferRuntime runtime =
                activeTransfers.get(taskId);

        if (runtime != null) {

            runtime.requestCancel();

            return true;
        }

        return false;
    }

    /**
     * Cancel All başlangıcı.
     *
     * Bu andan itibaren worker'lar yeni task alamaz.
     */
    public void beginCancelAll() {

        cancellingAll = true;
    }

    /**
     * Cancel All tamamlandıktan sonra worker'ların
     * tekrar queue'dan iş almasına izin ver.
     */
    public void endCancelAll() {

        cancellingAll = false;
    }

    public boolean isCancellingAll() {

        return cancellingAll;
    }

    /**
     * Bütün queued transfer'ları iptal eder.
     */
    public void cancelAll() {

        List<TransferRuntime> cancelled =
                new ArrayList<>();

        /*
         * cancellingAll zaten true olduğu için
         * worker'lar artık yeni iş alamıyor.
         */
        TransferRuntime transferRuntime;

        while ((transferRuntime =
                queue.poll()) != null) {

            cancelled.add(
                    transferRuntime);
        }

        /*
         * Queue boşaltıldıktan sonra cancellation
         * state'lerini ver.
         */
        for (TransferRuntime runtime :
                cancelled) {

            runtime.requestCancel();

            TransferGroup group =
                    runtime.getTask().getGroup();

            if (group != null) {
                group.cancelled();
            }

            runtime.setEndTime(
                    Instant.now());

            runtime.setStatus(
                    TransferStatus.CANCELLED);

            runtime.setMessage(
                    "Transfer cancelled");
        }

        if (!cancelled.isEmpty()) {

            eventBus.publishBatch(
                    cancelled);
        }

        /*
         * Çalışan transferleri öldürme.
         *
         * Sadece cancellation request gönder.
         */
        for (TransferRuntime activeRuntime :
                activeTransfers.values()) {

            activeRuntime.requestCancel();
        }
    }

    private void cancelRuntime(
            TransferRuntime runtime) {

        runtime.requestCancel();

        TransferGroup group =
                runtime.getTask().getGroup();

        if (group != null) {
            group.cancelled();
        }

        runtime.setEndTime(
                Instant.now());

        runtime.setStatus(
                TransferStatus.CANCELLED);

        runtime.setMessage(
                "Transfer cancelled");

        eventBus.publish(runtime);
    }
}