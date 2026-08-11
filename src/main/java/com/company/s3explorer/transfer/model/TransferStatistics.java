package com.company.s3explorer.transfer.model;

import java.util.concurrent.atomic.AtomicInteger;

public class TransferStatistics {
    private final AtomicInteger queued;
    private final AtomicInteger running;
    private final AtomicInteger completed;
    private final AtomicInteger failed;
    private final AtomicInteger cancelled;


    public TransferStatistics(AtomicInteger queued, AtomicInteger running, AtomicInteger completed, AtomicInteger failed, AtomicInteger cancelled) {
        this.queued = queued;
        this.running = running;
        this.completed = completed;
        this.failed = failed;
        this.cancelled = cancelled;
    }
}
