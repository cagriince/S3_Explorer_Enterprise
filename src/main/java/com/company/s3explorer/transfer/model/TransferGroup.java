package com.company.s3explorer.transfer.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class TransferGroup {

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

        queued.decrementAndGet();
        running.incrementAndGet();
    }

    public void completed() {

        running.decrementAndGet();
        completed.incrementAndGet();

        checkCompletion();
    }

    public void failed() {

        running.decrementAndGet();
        failed.incrementAndGet();

        checkCompletion();
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

            Runnable callback =
                    completionCallback;

            if (callback != null) {
                callback.run();
            }
        }
    }
}