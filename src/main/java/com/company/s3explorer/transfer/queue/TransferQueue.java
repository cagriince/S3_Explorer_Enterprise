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

public class TransferQueue {

    private final BlockingQueue<TransferRuntime> queue =
            new LinkedBlockingQueue<>();

    private final Map<UUID, TransferRuntime> activeTransfers =
            new ConcurrentHashMap<>();

    private final TransferEventBus eventBus;

    public TransferQueue(
            TransferEventBus eventBus) {

        this.eventBus = eventBus;
    }

    public TransferRuntime add(
            TransferTask task) {

        TransferRuntime runtime =
                new TransferRuntime(task);

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

        return queue.take();
    }

    public TransferRuntime poll(
            long timeoutMillis)
            throws InterruptedException {

        return queue.poll(
                timeoutMillis,
                java.util.concurrent.TimeUnit.MILLISECONDS);
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
     * Bütün queued transfer'ları iptal eder.
     *
     * Bu metod artık UI/EDT üzerinden çağrılmamalıdır.
     * TransferManager bunu background thread'de çalıştırır.
     */
    public void cancelAll() {

        List<TransferRuntime> cancelled =
                new ArrayList<>();

        TransferRuntime transferRuntime;

        while ((transferRuntime = queue.poll()) != null) {

            cancelled.add(transferRuntime);
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

        eventBus.publishBatch(cancelled);

        /*
         * Çalışan transferleri öldürmeye çalışma.
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