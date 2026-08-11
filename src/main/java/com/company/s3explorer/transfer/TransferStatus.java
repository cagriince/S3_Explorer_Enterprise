package com.company.s3explorer.transfer;

public enum TransferStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }

    public boolean isFinished() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED;
    }
}