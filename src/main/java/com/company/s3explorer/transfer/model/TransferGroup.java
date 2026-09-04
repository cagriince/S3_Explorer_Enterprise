package com.company.s3explorer.transfer.model;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bir klasör transferinin bütün dosya görevlerini temsil eden
 * mantıksal transfer grubudur.
 *
 * Group tek bir UI satırı olarak gösterilebilir;
 * gerçek transferler ise TransferTask olarak çalışmaya devam eder.
 */
public class TransferGroup {

    private final UUID id;
    private final String displayName;

    /* Discovery / preparation */
    private final AtomicLong detected = new AtomicLong();
    private final AtomicLong detectedBytes = new AtomicLong();

    /* Task states */
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger cancelled = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    /* Failed task details */
    private final List<TransferTask> failedTasks =
            new CopyOnWriteArrayList<>();

    /* Producer state */
    private final AtomicInteger activeProducers = new AtomicInteger();

    private final AtomicBoolean productionCompleted =
            new AtomicBoolean(false);

    private final AtomicBoolean productionFailed =
            new AtomicBoolean(false);

    private final AtomicReference<Runnable> completionCallback =
            new AtomicReference<>();

    public TransferGroup(UUID id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    // ---------------------------------------------------------------------
    // PRODUCER / DISCOVERY
    // ---------------------------------------------------------------------

    public void detected() {
        detected.incrementAndGet();
    }

    public void detected(long size) {
        detected.incrementAndGet();

        if (size > 0) {
            detectedBytes.addAndGet(size);
        }
    }

    public long getDetected() {
        return detected.get();
    }

    public long getDetectedBytes() {
        return detectedBytes.get();
    }

    public void producerStarted() {
        activeProducers.incrementAndGet();
    }

    public void producerFinished() {
        decrementIfPositive(activeProducers);
        fireCompletionIfNecessary();
    }

    public int getActiveProducers() {
        return activeProducers.get();
    }

    public void markProductionCompleted() {
        productionCompleted.set(true);
        fireCompletionIfNecessary();
    }

    public void markProductionFailed() {
        productionFailed.set(true);
        productionCompleted.set(true);
        fireCompletionIfNecessary();
    }

    public boolean isProductionCompleted() {
        return productionCompleted.get();
    }

    public boolean isProductionFailed() {
        return productionFailed.get();
    }

    // ---------------------------------------------------------------------
    // TASK STATES
    // ---------------------------------------------------------------------

    public void queued() {
        queued.incrementAndGet();
    }

    public void running() {
        decrementIfPositive(queued);
        running.incrementAndGet();
    }

    public void completed() {
        decrementIfPositive(running);
        completed.incrementAndGet();

        fireCompletionIfNecessary();
    }

    public void completed(TransferTask task) {
        completed();
    }

    public void failed() {
        decrementIfPositive(running);
        failed.incrementAndGet();

        fireCompletionIfNecessary();
    }

    public void failed(TransferTask task) {
        failed();

        if (task != null) {
            failedTasks.add(task);
        }
    }

    public void cancelled() {
        if (queued.get() > 0) {
            decrementIfPositive(queued);
        } else {
            decrementIfPositive(running);
        }

        cancelled.incrementAndGet();

        fireCompletionIfNecessary();
    }

    public void cancelled(TransferTask task) {
        cancelled();
    }

    public void skipped() {
        skipped.incrementAndGet();
        fireCompletionIfNecessary();
    }

    public void skipped(TransferTask task) {
        skipped();
    }

    // ---------------------------------------------------------------------
    // GETTERS
    // ---------------------------------------------------------------------

    public int getQueued() {
        return queued.get();
    }

    public int getRunning() {
        return running.get();
    }

    public int getCompleted() {
        return completed.get();
    }

    public int getFailed() {
        return failed.get();
    }

    public int getCancelled() {
        return cancelled.get();
    }

    public int getSkipped() {
        return skipped.get();
    }

    /**
     * Toplam tespit edilen görev sayısı.
     */
    public long getTotal() {
        return detected.get();
    }

    /**
     * Başarısız olan gerçek TransferTask nesneleri.
     */
    public List<TransferTask> getFailedTasks() {
        return List.copyOf(failedTasks);
    }

    // ---------------------------------------------------------------------
    // STATUS
    // ---------------------------------------------------------------------

    public boolean isPreparing() {
        return !productionCompleted.get()
                && activeProducers.get() > 0;
    }

    public boolean isRunning() {
        return !isFinished()
                && (
                queued.get() > 0
                        || running.get() > 0
                        || completed.get() > 0
                        || failed.get() > 0
                        || cancelled.get() > 0
                        || skipped.get() > 0
        );
    }

    /**
     * Producer üretimini tamamlamış ve artık bekleyen/çalışan
     * task kalmamışsa grup bitmiştir.
     */
    public boolean isFinished() {
        return productionCompleted.get()
                && activeProducers.get() == 0
                && queued.get() == 0
                && running.get() == 0;
    }

    public boolean isCompleted() {
        return isFinished()
                && !productionFailed.get()
                && failed.get() == 0;
    }

    public boolean isFailed() {
        return productionFailed.get()
                || failed.get() > 0;
    }

    /**
     * Tüm tespit edilen işler başarıyla tamamlandıysa true.
     */
    public boolean isFullySuccessful() {

        if (!isFinished()) {
            return false;
        }

        if (productionFailed.get()) {
            return false;
        }

        if (failed.get() > 0) {
            return false;
        }

        if (cancelled.get() > 0) {
            return false;
        }

        if (skipped.get() > 0) {
            return false;
        }

        return completed.get() == detected.get();
    }

    // ---------------------------------------------------------------------
    // COMPLETION CALLBACK
    // ---------------------------------------------------------------------

    public void setCompletionCallback(Runnable callback) {
        completionCallback.set(callback);
        fireCompletionIfNecessary();
    }

    private void fireCompletionIfNecessary() {

        if (!isFinished()) {
            return;
        }

        Runnable callback =
                completionCallback.getAndSet(null);

        if (callback != null) {
            callback.run();
        }
    }

    // ---------------------------------------------------------------------
    // INTERNAL
    // ---------------------------------------------------------------------

    private static void decrementIfPositive(
            AtomicInteger value) {

        value.updateAndGet(current ->
                current > 0 ? current - 1 : 0);
    }
}