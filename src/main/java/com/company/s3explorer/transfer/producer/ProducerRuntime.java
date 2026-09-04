package com.company.s3explorer.transfer.producer;

import com.company.s3explorer.transfer.TransferStatus;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProducerRuntime {

    private final String description;

    private volatile TransferStatus status =
            TransferStatus.QUEUED;

    private volatile long discoveredCount;

    private volatile String message = "";

    private volatile Instant startTime;
    private volatile Instant endTime;

    private volatile boolean cancelRequested;

    private volatile long lastUiPublishTime;

    private volatile Runnable progressCallback;

    /*
     * Producer gerçekten execution'a başladı mı?
     *
     * Bu state, Cancel All ile producer thread'inin
     * aynı anda başlaması durumundaki race-condition'ı
     * kontrol eder.
     */
    private final AtomicBoolean executionStarted =
            new AtomicBoolean(false);

    /*
     * Producer execution başlamadan önce cancellation
     * lifecycle'ı kapatıldı mı?
     */
    private final AtomicBoolean cancelledBeforeStart =
            new AtomicBoolean(false);

    public ProducerRuntime(
            String description) {

        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(
            TransferStatus status) {

        this.status = status;
    }

    public long getDiscoveredCount() {
        return discoveredCount;
    }

    public void incrementDiscovered() {

        discoveredCount++;

        if (shouldPublishUi(100)
                && progressCallback != null) {

            progressCallback.run();
        }
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(
            String message) {

        this.message = message;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(
            Instant startTime) {

        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(
            Instant endTime) {

        this.endTime = endTime;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void requestCancel() {
        cancelRequested = true;
    }

    public void setProgressCallback(
            Runnable progressCallback) {

        this.progressCallback =
                progressCallback;
    }

    public boolean shouldPublishUi(
            long intervalMillis) {

        long now =
                System.currentTimeMillis();

        if (now - lastUiPublishTime
                >= intervalMillis) {

            lastUiPublishTime = now;

            return true;
        }

        return false;
    }

    public void forceNextUiPublish() {
        lastUiPublishTime = 0;
    }

    public long getElapsedTime() {

        if (startTime == null) {
            return 0;
        }

        Instant end =
                endTime != null
                        ? endTime
                        : Instant.now();

        return java.time.Duration.between(
                startTime,
                end).toMillis();
    }

    public boolean isInterruptedOrCancelRequested() {

        return cancelRequested
                || Thread.currentThread().isInterrupted();
    }

    /**
     * Producer execution'ını ilk kez başlatan thread
     * bu metottan true alır.
     *
     * Cancel All önce davranmışsa false döner.
     */
    public boolean tryStartExecution() {

        return executionStarted.compareAndSet(
                false,
                true);
    }

    public boolean isExecutionStarted() {

        return executionStarted.get();
    }

    /**
     * Producer henüz başlamadıysa cancellation lifecycle'ını
     * ilk yapan taraf true alır.
     *
     * Producer zaten başladıysa false döner.
     */
    public boolean tryCancelBeforeStart() {

        if (executionStarted.get()) {
            return false;
        }

        return cancelledBeforeStart.compareAndSet(
                false,
                true);
    }

    public boolean isCancelledBeforeStart() {

        return cancelledBeforeStart.get();
    }
}