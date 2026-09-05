package com.company.s3explorer.transfer.state;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;

import java.util.*;

public class TransferStateStore {

    public static final int DEFAULT_VISIBLE_LIMIT = 1000;

    private final int visibleLimit;

    private final Map<UUID, TransferRuntime> runtimes =
            new HashMap<>();

    private final Deque<UUID> allOrder =
            new ArrayDeque<>();

    private final Deque<UUID> queuedOrder =
            new ArrayDeque<>();

    private final Deque<UUID> runningOrder =
            new ArrayDeque<>();

    private final Deque<UUID> finishedOrder =
            new ArrayDeque<>();

    private final Map<UUID, TransferStatus> statuses =
            new HashMap<>();

    private long queuedCount;
    private long runningCount;
    private long completedCount;
    private long failedCount;
    private long cancelledCount;

    /*
     * UI'nin gereksiz yere tabloyu yeniden çizmesini
     * engellemek için kullanılır.
     *
     * Her gerçek state değişiminde artırılır.
     */
    private long version;

    public TransferStateStore() {
        this(DEFAULT_VISIBLE_LIMIT);
    }

    public TransferStateStore(int visibleLimit) {

        if (visibleLimit < 1) {
            throw new IllegalArgumentException(
                    "visibleLimit must be greater than zero");
        }

        this.visibleLimit = visibleLimit;
    }

    public synchronized void upsert(
            TransferRuntime runtime) {

        if (runtime == null
                || runtime.getTask() == null) {
            return;
        }

        UUID id =
                runtime.getTask().getId();

        TransferStatus newStatus =
                runtime.getStatus();

        TransferStatus oldStatus =
                statuses.put(
                        id,
                        newStatus);

        runtimes.put(
                id,
                runtime);

        /*
         * İlk kez görüyoruz.
         */
        if (oldStatus == null) {

            increment(newStatus);

            allOrder.addFirst(id);

            addToStatusOrder(
                    newStatus,
                    id);

            version++;

            return;
        }

        /*
         * Status değiştiyse eski görünümden çıkar,
         * yeni görünüme ekle.
         */
        if (oldStatus != newStatus) {

            decrement(oldStatus);
            increment(newStatus);

            removeFromStatusOrder(
                    oldStatus,
                    id);

            addToStatusOrder(
                    newStatus,
                    id);
        }

        /*
         * All görünümünde son güncellenen kayıt
         * en üstte görünsün.
         */
        addNewest(
                allOrder,
                id);

        version++;
    }

    public synchronized void upsertAll(
            Collection<TransferRuntime> runtimes) {

        if (runtimes == null
                || runtimes.isEmpty()) {
            return;
        }

        boolean changed = false;

        for (TransferRuntime runtime : runtimes) {

            if (runtime == null
                    || runtime.getTask() == null) {
                continue;
            }

            UUID id =
                    runtime.getTask().getId();

            TransferStatus newStatus =
                    runtime.getStatus();

            TransferStatus oldStatus =
                    statuses.put(
                            id,
                            newStatus);

            this.runtimes.put(
                    id,
                    runtime);

            /*
             * İlk kez görüyoruz.
             */
            if (oldStatus == null) {

                increment(newStatus);

                allOrder.addFirst(id);

                addToStatusOrder(
                        newStatus,
                        id);

                changed = true;

                continue;
            }

            /*
             * Status gerçekten değiştiyse
             * sayaçları ve status index'lerini güncelle.
             */
            if (oldStatus != newStatus) {

                decrement(oldStatus);
                increment(newStatus);

                /*
                 * CANCEL ALL sırasında binlerce
                 * QUEUED -> CANCELLED geçişi olabilir.
                 *
                 * Bu durumda:
                 *
                 *     queuedOrder.remove(id)
                 *
                 * kullanmak O(n) olduğu için toplamda
                 * O(n²) maliyet oluşturur.
                 *
                 * Bu nedenle burada normal tekil
                 * güncelleme yapılmaya devam ediyor.
                 *
                 * Bulk cancellation optimizasyonu
                 * aşağıdaki özel metod üzerinden yapılır.
                 */
                removeFromStatusOrder(
                        oldStatus,
                        id);

                addToStatusOrder(
                        newStatus,
                        id);

                changed = true;

            } else {

                /*
                 * Runtime'ın başka bir alanı
                 * değişmiş olabilir.
                 */
                changed = true;
            }

            /*
             * All görünümünde son güncellenen kayıt
             * en üstte görünsün.
             */
            addNewest(
                    allOrder,
                    id);
        }

        if (changed) {
            version++;
        }
    }

    public synchronized void markAllQueuedCancelled(
            Collection<TransferRuntime> cancelledRuntimes) {

        if (cancelledRuntimes == null
                || cancelledRuntimes.isEmpty()) {
            return;
        }

        /*
         * Cancel All sırasında queued kayıtların tamamı
         * zaten QUEUED durumundadır.
         *
         * Bu nedenle queuedOrder içinden ID'leri tek tek
         *
         *     queuedOrder.remove(id)
         *
         * ile aramıyoruz.
         *
         * Bunun yerine bütün QUEUED index'ini tek işlemle
         * temizliyoruz.
         */
        queuedOrder.clear();

        long transitionedCount = 0;

        /*
         * Önce runtime ve status map'lerini güncelle.
         */
        for (TransferRuntime runtime :
                cancelledRuntimes) {

            if (runtime == null
                    || runtime.getTask() == null) {
                continue;
            }

            UUID id =
                    runtime.getTask().getId();

            TransferStatus oldStatus =
                    statuses.get(id);

            /*
             * StateStore'da olmayan kayıt.
             *
             * Normal kayıt olarak ekle.
             */
            if (oldStatus == null) {

                statuses.put(
                        id,
                        TransferStatus.CANCELLED);

                runtimes.put(
                        id,
                        runtime);

                cancelledCount++;

                transitionedCount++;

                /*
                 * All listesine ekle.
                 *
                 * Bu yeni bir kayıt olduğu için başa eklenebilir.
                 */
                allOrder.addFirst(id);

                continue;
            }

            /*
             * Zaten cancelled ise tekrar sayaç artırma.
             */
            if (oldStatus == TransferStatus.CANCELLED) {

                runtimes.put(
                        id,
                        runtime);

                continue;
            }

            /*
             * Beklenen durum:
             *
             * QUEUED -> CANCELLED
             */
            if (oldStatus == TransferStatus.QUEUED) {

                if (queuedCount > 0) {
                    queuedCount--;
                }

                cancelledCount++;

                transitionedCount++;

                statuses.put(
                        id,
                        TransferStatus.CANCELLED);

                runtimes.put(
                        id,
                        runtime);

                continue;
            }

            /*
             * Güvenlik:
             *
             * Beklenmeyen bir status varsa normal
             * transition uygula.
             */
            decrement(oldStatus);

            increment(
                    TransferStatus.CANCELLED);

            statuses.put(
                    id,
                    TransferStatus.CANCELLED);

            runtimes.put(
                    id,
                    runtime);

            removeFromStatusOrder(
                    oldStatus,
                    id);

            transitionedCount++;
        }

        /*
         * CANCELLED kayıtlarını Finished index'ine ekle.
         *
         * Burada contains() kullanmıyoruz.
         *
         * Çünkü cancelledRuntimes içindeki kayıtlar bu
         * bulk operation tarafından CANCELLED durumuna
         * geçirildi ve Finished listesinde henüz
         * bulunmuyorlar.
         */
        for (TransferRuntime runtime :
                cancelledRuntimes) {

            if (runtime == null
                    || runtime.getTask() == null) {
                continue;
            }

            UUID id =
                    runtime.getTask().getId();

            TransferStatus status =
                    statuses.get(id);

            if (status
                    == TransferStatus.CANCELLED) {

                finishedOrder.addFirst(id);
            }
        }

        /*
         * StateStore'a yalnızca bir kez state değişikliği
         * olduğunu bildir.
         */
        if (transitionedCount > 0) {
            version++;
        }
    }
    
    public synchronized TransferRuntime get(
            UUID id) {

        return runtimes.get(id);
    }

    public synchronized List<TransferRuntime> snapshot(
            View view) {

        Deque<UUID> source =
                switch (view) {

                    case QUEUED ->
                            queuedOrder;

                    case RUNNING ->
                            runningOrder;

                    case FINISHED ->
                            finishedOrder;

                    case ALL ->
                            allOrder;
                };

        List<TransferRuntime> result =
                new ArrayList<>(
                        Math.min(
                                source.size(),
                                visibleLimit));

        int count = 0;

        for (UUID id : source) {

            TransferRuntime runtime =
                    runtimes.get(id);

            if (runtime == null) {
                continue;
            }

            result.add(runtime);

            count++;

            if (count >= visibleLimit) {
                break;
            }
        }

        return result;
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized long getQueuedCount() {
        return queuedCount;
    }

    public synchronized long getRunningCount() {
        return runningCount;
    }

    public synchronized long getCompletedCount() {
        return completedCount;
    }

    public synchronized long getFailedCount() {
        return failedCount;
    }

    public synchronized long getCancelledCount() {
        return cancelledCount;
    }

    public synchronized long getFinishedCount() {

        return completedCount
                + failedCount
                + cancelledCount;
    }

    public synchronized long getTotalCount() {
        return runtimes.size();
    }

    public int getVisibleLimit() {
        return visibleLimit;
    }

    public synchronized void removeFinished() {

        if (finishedOrder.isEmpty()) {
            return;
        }

        /*
         * Finished kayıtları runtimes/statuses içinden çıkar.
         *
         * Burada UUID'leri tek tek ArrayDeque'den
         * remove(Object) ile çıkarmıyoruz.
         */
        for (UUID id : finishedOrder) {

            runtimes.remove(id);
            statuses.remove(id);
        }

        /*
         * Finished kayıtlarının tamamını tek seferde
         * temizliyoruz.
         */
        finishedOrder.clear();

        /*
         * All listesini yeniden oluşturuyoruz.
         *
         * Böylece 233.000 kez:
         *
         * allOrder.remove(id)
         *
         * yapmıyoruz.
         */
        Deque<UUID> newAllOrder =
                new ArrayDeque<>();

        for (UUID id : allOrder) {

            if (runtimes.containsKey(id)) {
                newAllOrder.addLast(id);
            }
        }

        allOrder.clear();
        allOrder.addAll(newAllOrder);

        /*
         * Finished sayaçlarını sıfırla.
         */
        completedCount = 0;
        failedCount = 0;
        cancelledCount = 0;

        version++;
    }
    
    private void increment(
            TransferStatus status) {

        if (status == null) {
            return;
        }

        switch (status) {

            case QUEUED ->
                    queuedCount++;

            case RUNNING ->
                    runningCount++;

            case COMPLETED ->
                    completedCount++;

            case FAILED ->
                    failedCount++;

            case CANCELLED ->
                    cancelledCount++;
        }
    }

    private void decrement(
            TransferStatus status) {

        if (status == null) {
            return;
        }

        switch (status) {

            case QUEUED ->
                    queuedCount--;

            case RUNNING ->
                    runningCount--;

            case COMPLETED ->
                    completedCount--;

            case FAILED ->
                    failedCount--;

            case CANCELLED ->
                    cancelledCount--;
        }
    }

    private void addToStatusOrder(
            TransferStatus status,
            UUID id) {

        if (status == TransferStatus.QUEUED) {

            addNewest(
                    queuedOrder,
                    id);

        } else if (status == TransferStatus.RUNNING) {

            addNewest(
                    runningOrder,
                    id);

        } else if (status != null
                && status.isFinished()) {

            addNewest(
                    finishedOrder,
                    id);
        }
    }

    private void removeFromStatusOrder(
            TransferStatus status,
            UUID id) {

        if (status == TransferStatus.QUEUED) {

            queuedOrder.remove(id);

        } else if (status == TransferStatus.RUNNING) {

            runningOrder.remove(id);

        } else if (status != null
                && status.isFinished()) {

            finishedOrder.remove(id);
        }
    }

    private void addNewest(
            Deque<UUID> deque,
            UUID id) {

        if (!deque.isEmpty()
                && id.equals(deque.peekFirst())) {

            return;
        }

        deque.remove(id);
        deque.addFirst(id);
    }

    private void removeFromAllOrders(
            UUID id) {

        allOrder.remove(id);
        queuedOrder.remove(id);
        runningOrder.remove(id);
        finishedOrder.remove(id);
    }

    public enum View {

        QUEUED,
        RUNNING,
        FINISHED,
        ALL
    }
}