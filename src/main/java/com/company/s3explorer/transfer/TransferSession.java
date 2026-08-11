package com.company.s3explorer.transfer;

import com.company.s3explorer.transfer.model.TransferTask;

import java.time.Instant;

public class TransferSession {

    private final TransferTask task;
    private TransferStatus status;
    private int progress;
    private String message;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;

    public TransferSession(TransferTask task) {
        this.task = task;
        this.status = TransferStatus.QUEUED;
        this.progress = 0;
        this.queuedAt = Instant.now();
    }

    public TransferTask getTask() {
        return task;
    }
}