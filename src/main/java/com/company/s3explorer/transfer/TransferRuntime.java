package com.company.s3explorer.transfer;

import com.company.s3explorer.transfer.model.TransferTask;

import java.time.Duration;
import java.time.Instant;

public class TransferRuntime {

    private final TransferTask task;
    private TransferStatus status = TransferStatus.QUEUED;
    private String message = "";

    private long transferredBytes;
    private long totalBytes;

    private volatile boolean cancelRequested;
    private volatile Instant startTime;
    private volatile Instant endTime;
    private volatile long lastUiPublishTime;

    public TransferRuntime(TransferTask task) {
        this.task = task;
    }

    public TransferTask getTask() {
        return task;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void requestCancel() {
        cancelRequested = true;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public void updateProgress(long transferred, long total) {
        this.transferredBytes = transferred;
        this.totalBytes = total;
    }

    public void progressCompleted() {
        transferredBytes = totalBytes;
    }

    public int getPercent() {
        if (totalBytes == 0) {
            return 0;
        }

        return (int) ((transferredBytes * 100) / totalBytes);
    }

    public long getElapsedTime() {
        if (this.startTime == null) {
            return 0;
        }

        return Duration.between(
                this.startTime,
                this.endTime != null ? this.endTime : Instant.now()
        ).toMillis();
    }

    /**
     * Progress event'lerinin UI'ı boğmasını engeller.
     */
    public boolean shouldPublishUi(long intervalMillis) {
        long now = System.currentTimeMillis();

        if (now - lastUiPublishTime >= intervalMillis) {
            lastUiPublishTime = now;
            return true;
        }

        return false;
    }

    /**
     * İş tamamlandığında son event mutlaka yayınlanabilsin.
     */
    public void forceNextUiPublish() {
        lastUiPublishTime = 0;
    }
}