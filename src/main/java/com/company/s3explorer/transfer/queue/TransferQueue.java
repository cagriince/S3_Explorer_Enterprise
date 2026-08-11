package com.company.s3explorer.transfer.queue;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.model.TransferTask;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class TransferQueue {
    private final BlockingQueue<TransferRuntime> queue = new LinkedBlockingQueue<>();
    private final Map<UUID, TransferRuntime> activeTransfers = new ConcurrentHashMap<>();
    private final TransferEventBus eventBus;

    public TransferQueue(TransferEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public TransferRuntime add(TransferTask task) {
        TransferRuntime runtime = new TransferRuntime(task);
        runtime.setStatus(TransferStatus.QUEUED);
        queue.add(runtime);
        eventBus.publish(runtime);

        return runtime;
    }

    public TransferRuntime take() throws InterruptedException {
        return queue.take();
    }

    public void markActive(TransferRuntime runtime) {
        activeTransfers.put(runtime.getTask().getId(), runtime);
    }

    public void markFinished(TransferRuntime runtime) {
        activeTransfers.remove(runtime.getTask().getId());
    }

    public boolean cancel(UUID taskId) {
        for (TransferRuntime runtime : queue) {
            if (!runtime.getTask().getId().equals(taskId)) {
                continue;
            }

            if (queue.remove(runtime)) {
                cancelRuntime(runtime);
                return true;
            }
        }

        TransferRuntime runtime = activeTransfers.get(taskId);
        if (runtime != null) {
            runtime.requestCancel();
            return true;
        }

        return false;
    }

    public void cancelAll() {
        Iterator<TransferRuntime> it = queue.iterator();
        while (it.hasNext()) {
            TransferRuntime runtime = it.next();
            it.remove();
            cancelRuntime(runtime);
        }

        for (TransferRuntime runtime : activeTransfers.values()) {
            runtime.requestCancel();
        }
    }

    private void cancelRuntime(TransferRuntime runtime) {
        runtime.requestCancel();
        runtime.setEndTime(Instant.now());
        runtime.setStatus(TransferStatus.CANCELLED);
        runtime.setMessage("Transfer cancelled");
        eventBus.publish(runtime);
    }
}