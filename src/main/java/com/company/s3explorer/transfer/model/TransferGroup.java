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

    private final AtomicInteger skipped =
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
     * Number of producers that are currently responsible for
     * producing tasks for this group.
     *
     * This allows multiple folder producers to share the same
     * TransferGroup safely.
     */
    private final AtomicInteger activeProducers =
            new AtomicInteger();

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
                "[GROUP RUNNING] {} queued={} running={} completed={} failed={} cancelled={} skipped={} productionCompleted={} activeProducers={}",
                displayName,
                q,
                r,
                completed.get(),
                failed.get(),
                cancelled.get(),
                skipped.get(),
                productionCompleted,
                activeProducers.get());
    }

    public void completed() {

        int r =
                running.decrementAndGet();

        int c =
                completed.incrementAndGet();

        log.debug(
                "[GROUP COMPLETED TASK] {} queued={} running={} completed={} failed={} cancelled={} skipped={} productionCompleted={} activeProducers={}",
                displayName,
                queued.get(),
                r,
                c,
                failed.get(),
                cancelled.get(),
                skipped.get(),
                productionCompleted,
                activeProducers.get());

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
     * Marks one accepted operation as skipped.
     *
     * A skipped operation is not a transfer task and therefore
     * does not affect queued/running/completed/failed counters.
     *
     * It is nevertheless part of the final group result.
     */
    public void skipped() {

        int count =
                skipped.incrementAndGet();

        log.debug(
                "[GROUP SKIPPED] {} queued={} running={} completed={} failed={} cancelled={} skipped={}",
                displayName,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get(),
                count);
    }

    /**
     * Returns the number of operations skipped before
     * transfer submission.
     */
    public int getSkipped() {

        return skipped.get();
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
     * Registers a producer as an active producer of this group.
     *
     * This must be called before the producer starts producing
     * tasks.
     */
    public void registerProducer() {

        int count =
                activeProducers.incrementAndGet();

        productionCompleted = false;

        log.debug(
                "[GROUP PRODUCER REGISTERED] {} activeProducers={}",
                displayName,
                count);
    }

    /**
     * Marks one registered producer as finished.
     *
     * Production is considered completed only when the last
     * registered producer has finished.
     */
    public void producerCompleted() {

        int remaining =
                activeProducers.decrementAndGet();

        if (remaining < 0) {

            activeProducers.incrementAndGet();

            log.warn(
                    "[GROUP PRODUCER COMPLETION] {} producerCompleted called without a registered producer",
                    displayName);

            return;
        }

        log.debug(
                "[GROUP PRODUCER COMPLETED] {} activeProducers={} queued={} running={} completed={} failed={} cancelled={} skipped={}",
                displayName,
                remaining,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get(),
                skipped.get());

        if (remaining == 0) {

            productionCompleted = true;

            log.debug(
                    "[GROUP PRODUCTION COMPLETED] {} queued={} running={} completed={} failed={} cancelled={} skipped={}",
                    displayName,
                    queued.get(),
                    running.get(),
                    completed.get(),
                    failed.get(),
                    cancelled.get(),
                    skipped.get());

            checkCompletion();
        }
    }

    /**
     * Backward-compatible lifecycle completion method.
     *
     * Used by operations that do not register producers.
     */
    public void markProductionCompleted() {

        if (activeProducers.get() > 0) {

            log.debug(
                    "[GROUP PRODUCTION COMPLETED] {} ignored because activeProducers={}",
                    displayName,
                    activeProducers.get());

            return;
        }

        productionCompleted = true;

        log.debug(
                "[GROUP PRODUCTION COMPLETED] {} queued={} running={} completed={} failed={} cancelled={} skipped={}",
                displayName,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get(),
                skipped.get());

        checkCompletion();
    }

    public boolean isProductionCompleted() {

        return productionCompleted;
    }

    public int getActiveProducers() {

        return activeProducers.get();
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
                + skipped.get()
                + queued.get()
                + running.get();
    }

    public boolean isFinished() {

        return productionCompleted
                && activeProducers.get() == 0
                && queued.get() == 0
                && running.get() == 0;
    }

    public boolean isFullySuccessful() {

        return isFinished()
                && failed.get() == 0
                && cancelled.get() == 0
                && skipped.get() == 0;
    }

    private void checkCompletion() {

        log.debug(
                "[GROUP CHECK] {} queued={} running={} completed={} failed={} cancelled={} skipped={} productionCompleted={} activeProducers={}",
                displayName,
                queued.get(),
                running.get(),
                completed.get(),
                failed.get(),
                cancelled.get(),
                skipped.get(),
                productionCompleted,
                activeProducers.get());

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