package com.company.s3explorer.transfer.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class TransferGroup {
    private final UUID id;
    private final String displayName;

    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger cancelled = new AtomicInteger();

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
    }

    public void failed() {
        running.decrementAndGet();
        failed.incrementAndGet();
    }

    public void cancelled() {
        if (queued.get() > 0) {
            queued.decrementAndGet();
        } else {
            running.decrementAndGet();
        }

        cancelled.incrementAndGet();
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
}
/*
public String getDisplayName() {
    return "%s [%d/%d]".formatted(
            this.getName(),
            this.getIndex(),
            this.getSize());
}

 */