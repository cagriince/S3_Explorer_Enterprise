package com.company.s3explorer.transfer.producer;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProducerRuntime {

    private volatile boolean cancelRequested;

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

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void requestCancel() {
        cancelRequested = true;
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
}