package com.company.s3explorer.transfer.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransferGroup {

    private final UUID id;
    private final String displayName;

    /*
     * Producer tarafından keşfedilen toplam nesne sayısı.
     */
    private final AtomicLong detected = new AtomicLong();

    /*
     * Producer tarafından keşfedilen toplam byte.
     */
    private final AtomicLong detectedBytes = new AtomicLong();

    /*
     * Kuyruğa alınmış fakat henüz çalışmaya başlamamış task sayısı.
     */
    private final AtomicInteger queued = new AtomicInteger();

    /*
     * Şu anda çalışan task sayısı.
     */
    private final AtomicInteger running = new AtomicInteger();

    /*
     * Başarıyla tamamlanan task sayısı.
     */
    private final AtomicInteger completed = new AtomicInteger();

    /*
     * Hata ile sonuçlanan task sayısı.
     */
    private final AtomicInteger failed = new AtomicInteger();

    /*
     * İptal edilen task sayısı.
     */
    private final AtomicInteger cancelled = new AtomicInteger();

    /*
     * Producer bütün nesneleri keşfedip task üretmeyi bitirdi mi?
     *
     * Önemli:
     * false iken group henüz "Preparing" durumundadır.
     */
    private final AtomicBoolean productionCompleted =
            new AtomicBoolean(false);

    /*
     * Producer çalışırken hata oluştu mu?
     *
     * Örneğin S3 listObjects sırasında hata oluşması gibi.
     */
    private final AtomicBoolean productionFailed =
            new AtomicBoolean(false);

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

    // ------------------------------------------------------------------
    // Producer / discovery
    // ------------------------------------------------------------------

    /**
     * Producer bir nesne keşfettiğinde çağrılır.
     */
    public void detected() {
        detected.incrementAndGet();
    }

    /**
     * Producer bir nesne keşfettiğinde ve boyutunu biliyorsa çağrılır.
     */
    public void detected(long size) {
        detected.incrementAndGet();

        if (size > 0) {
            detectedBytes.addAndGet(size);
        }
    }

    public long getDetected() {
        return detected.get();
    }

    public long getDetectedBytes() {
        return detectedBytes.get();
    }

    /**
     * Producer bütün task'ları üretmeyi tamamladı.
     */
    public void markProductionCompleted() {
        productionCompleted.set(true);
    }

    /**
     * Producer hata nedeniyle tamamlandı.
     */
    public void markProductionFailed() {
        productionFailed.set(true);
        productionCompleted.set(true);
    }

    public boolean isProductionCompleted() {
        return productionCompleted.get();
    }

    public boolean isProductionFailed() {
        return productionFailed.get();
    }

    // ------------------------------------------------------------------
    // Task lifecycle
    // ------------------------------------------------------------------

    /**
     * Yeni bir task group'a eklendi.
     */
    public void queued() {
        queued.incrementAndGet();
    }

    /**
     * Kuyruktaki task çalışmaya başladı.
     */
    public void running() {
        decrementIfPositive(queued);
        running.incrementAndGet();
    }

    /**
     * Task başarıyla tamamlandı.
     */
    public void completed() {
        decrementIfPositive(running);
        completed.incrementAndGet();
    }

    /**
     * Task hata ile sonuçlandı.
     */
    public void failed() {
        decrementIfPositive(running);
        failed.incrementAndGet();
    }

    /**
     * Task iptal edildi.
     *
     * Task henüz queue'da ise queued azalır.
     * Çalışıyorsa running azalır.
     */
    public void cancelled() {

        if (!decrementIfPositive(queued)) {
            decrementIfPositive(running);
        }

        cancelled.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Counters
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Group lifecycle
    // ------------------------------------------------------------------

    /**
     * Producer tamamlandı ve artık queue/running task kalmadı.
     *
     * Bu noktada group'un gerçekten bitmiş olduğunu söyleyebiliriz.
     */
    public boolean isFinished() {
        return productionCompleted.get()
                && queued.get() == 0
                && running.get() == 0;
    }

    /**
     * Group başarıyla tamamlandı.
     *
     * Producer hata vermemiş,
     * task hatası oluşmamış,
     * task iptal edilmemiş
     * ve bütün task'lar bitmiş olmalıdır.
     */
    public boolean isCompleted() {
        return isFinished()
                && !productionFailed.get()
                && failed.get() == 0
                && cancelled.get() == 0;
    }

    /**
     * Group herhangi bir nedenle başarısız oldu.
     */
    public boolean isFailed() {
        return productionFailed.get()
                || failed.get() > 0;
    }

    /**
     * Group hâlâ producer aşamasında.
     *
     * Henüz tüm nesneler keşfedilmemiştir.
     */
    public boolean isPreparing() {
        return !productionCompleted.get();
    }

    /**
     * Producer bitmiş ve task'lar çalışıyor veya bekliyorsa
     * group Running kabul edilir.
     */
    public boolean isRunning() {
        return productionCompleted.get()
                && (queued.get() > 0 || running.get() > 0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean decrementIfPositive(AtomicInteger counter) {

        while (true) {

            int current = counter.get();

            if (current <= 0) {
                return false;
            }

            if (counter.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }
}