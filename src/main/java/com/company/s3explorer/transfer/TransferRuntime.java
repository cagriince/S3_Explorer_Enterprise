package com.company.s3explorer.transfer;

import com.company.s3explorer.transfer.model.TransferProgress;
import com.company.s3explorer.transfer.model.TransferTask;

import java.time.Duration;
import java.time.Instant;

public class TransferRuntime {

    private final TransferTask task;
    private TransferStatus status = TransferStatus.QUEUED;
    private String message = "";
    private final TransferProgress progress = new TransferProgress();
    private volatile boolean cancelRequested;

    private volatile Instant startTime;
    private volatile Instant endTime;
    private volatile long lastUiPublishTime;

    private Throwable exception;

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

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public void updateProgress(long transferred, long total) {
        progress.update(transferred, total);
    }

    public void progressCompleted() {
        progress.complete();
    }

    public int getPercent() {
        return progress.getPercent();
    }

    public long getElapsedTime() {
        if (this.startTime ==null) {
            // not started yet,in queue
            return 0;
        }
        return Duration.between(this.startTime, this.endTime != null ? this.endTime : Instant.now()).toMillis();
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