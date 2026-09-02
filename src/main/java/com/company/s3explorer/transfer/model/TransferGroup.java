package com.company.s3explorer.transfer.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TransferGroup {

    private static final Logger log =
            LoggerFactory.getLogger(TransferGroup.class);

    private final UUID id;
    private final String displayName;

    private final AtomicInteger queued =
            new AtomicInteger();

    private final AtomicInteger running =
            new AtomicInteger();

    private final AtomicInteger completed =
            new AtomicInteger();

    private final AtomicInteger failed =
            new AtomicInteger();

    private final AtomicInteger cancelled =
            new AtomicInteger();

    /*
     * Keeps the tasks that failed during execution.
     *
     * This is intentionally separate from the failed counter.
     * The counter is used for completion state while this list
     * is used by consumers that need to know which tasks failed.
     */
    private final List<TransferTask> failedTasks =
            new CopyOnWriteArrayList<>();

    /*
     * Producer artık yeni task üretmeyecek
     * anlamına gelir.
     */
    private volatile boolean productionCompleted;

    private volatile Runnable completionCallback;

    private volatile boolean completionPublished;

    public TransferGroup(
            UUID id,
            String displayName) {

        this.id = id;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setCompletionCallback(
            Runnable callback) {

        this.completionCallback = callback;
    }

    public void queued() {

        queued.incrementAndGet();
    }

    public void running() {

        int q =
                queued.decrementAndGet();

        int r =
                running.incrementAndGet();

        log.debug(
                "[GROUP RUNNING] {} queued={} running={} completed={} failed={} cancelled={} productionCompleted={}",
                displayName,
                q,
                r,
                completed.get(),
                failed.get(),
                cancelled.get(),
                productionCompleted);
    }

    public void completed() {

        int r =
                running.decrementAndGet();

        int c =
                completed.incrementAndGet();

        log.debug(
                "[GROUP COMPLETED TASK] {} queued={} running={} completed={} failed={} cancelled={} productionCompleted={}",
                displayName,
                queued.get(),
                r,
                c,
                failed.get(),
                cancelled.get(),
                productionCompleted);

        checkCompletion();
    }

    /**
     * Marks a task as failed and keeps the task reference
     * available for group-level completion handling.
     */
    public void failed(
            TransferTask task) {

        if (task != null) {

            failedTasks.add(task);
        }

        failed();
    }

    /**
     * Backward-compatible failure method.
     */
    public void failed() {

        running.decrementAndGet();
        failed.incrementAndGet();

        checkCompletion();
    }

    /**
     * Returns the tasks that failed during this group.
     *
     * The returned list is a snapshot and cannot be modified
     * by the caller.
     */
    public List<TransferTask> getFailedTasks() {

        return Collections.unmodifiableList(
                new ArrayList<>(failedTasks));
    }

    public void cancelled() {

        if (queued.get() > 0) {

            queued.decrementAndGet();

        } else if (running.get() > 0) {

            running.decrementAndGet();
        }

        cancelled.incrementAndGet();

        checkCompletion();
    }

    /**
     * Producer'ın artık yeni task üretmeyeceğini
     * bildirir.
     */
    public void markProductionCompleted() {

        productionCompleted = true;

        log.debug(
                "[GROUP PRODUCTION COMPLETED] {} queued={} running={} completed={} failed={} cancelled={}",
                displayName,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get());

        checkCompletion();
    }

    public boolean isProductionCompleted() {

        return productionCompleted;
    }

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

    public int getTotal() {

        return completed.get()
                + failed.get()
                + cancelled.get()
                + queued.get()
                + running.get();
    }

    public boolean isFinished() {

        return productionCompleted
                && queued.get() == 0
                && running.get() == 0;
    }

    public boolean isFullySuccessful() {

        return isFinished()
                && failed.get() == 0
                && cancelled.get() == 0;
    }

    private void checkCompletion() {

        log.debug(
                "[GROUP CHECK] {} queued={} running={} completed={} failed={} cancelled={} productionCompleted={}",
                displayName,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get(),
                productionCompleted);

        if (!isFinished()) {
            return;
        }

        if (completionPublished) {
            return;
        }

        synchronized (this) {

            if (completionPublished) {
                return;
            }

            if (!isFinished()) {
                return;
            }

            completionPublished = true;

            log.info(
                    "[GROUP FINISHED] {}",
                    displayName);

            Runnable callback =
                    completionCallback;

            if (callback != null) {
                callback.run();
            }
        }
    }
}