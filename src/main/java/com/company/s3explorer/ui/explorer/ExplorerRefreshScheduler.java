package com.company.s3explorer.ui.explorer;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ExplorerRefreshScheduler {

    private static final int DEFAULT_DELAY_MS = 500;

    private final Timer timer;

    private final Consumer<RefreshTreeNode> refreshAction;

    private final Runnable currentTableRefreshAction;

    /**
     * Transfer event'leri worker thread'lerinden gelebilir.
     *
     * Bu nedenle Swing Timer'a doğrudan dokunmuyoruz.
     */
    private final Set<RefreshTreeNode> pendingPrefixes =
            ConcurrentHashMap.newKeySet();

    private final AtomicBoolean refreshScheduled =
            new AtomicBoolean(false);

    private volatile boolean currentTableRefreshPending;

    public ExplorerRefreshScheduler(
            Consumer<RefreshTreeNode> refreshAction,
            Runnable currentTableRefreshAction) {

        this.refreshAction =
                refreshAction;

        this.currentTableRefreshAction =
                currentTableRefreshAction;

        timer =
                new Timer(
                        DEFAULT_DELAY_MS,
                        e -> executeRefresh());

        timer.setRepeats(false);
    }

    /**
     * Bir veya daha fazla folder tree node'unun
     * refresh edilmesini ister.
     *
     * Bu metod herhangi bir thread'den çağrılabilir.
     */
    public void scheduleRefresh(
            Collection<RefreshTreeNode> prefixes) {

        if (prefixes != null
                && !prefixes.isEmpty()) {

            for (RefreshTreeNode prefix :
                    prefixes) {

                if (prefix == null) {
                    continue;
                }

                System.out.println(
                        "[SCHEDULE TREE REFRESH] " +
                                "prefix=" +
                                prefix.prefix() +
                                " operation=" +
                                prefix.operation());
            }

            pendingPrefixes.addAll(prefixes);
        }

        scheduleTimer();
    }

    /**
     * Mevcut File Table'ın refresh edilmesini ister.
     *
     * Bu metod herhangi bir thread'den çağrılabilir.
     */
    public void scheduleCurrentTableRefresh() {

        currentTableRefreshPending = true;

        scheduleTimer();
    }

    /**
     * Refresh timer'ını güvenli şekilde kurar.
     *
     * Worker thread'lerinden gelen transfer event'leri
     * doğrudan Swing Timer'a dokunmaz.
     */
    private void scheduleTimer() {

        if (!refreshScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        SwingUtilities.invokeLater(() -> {

            refreshScheduled.set(false);

            /*
             * Yeni bir refresh isteği timer kurulmadan
             * önce geldiyse mevcut timer'ın süresini
             * yeniden başlatıyoruz.
             */
            timer.restart();
        });
    }

    private void executeRefresh() {

        /*
         * Önce o anda bekleyen refresh isteklerini
         * atomik olarak tüket.
         *
         * Böylece callback çalışırken yeni bir refresh
         * isteği gelirse kaybolmaz.
         */
        boolean refreshTable =
                currentTableRefreshPending;

        currentTableRefreshPending = false;

        /*
         * Current File Table
         */
        if (refreshTable) {

            currentTableRefreshAction.run();
        }

        /*
         * Folder Tree
         */
        if (!pendingPrefixes.isEmpty()) {

            Set<RefreshTreeNode> prefixes =
                    Set.copyOf(
                            pendingPrefixes);

            pendingPrefixes.removeAll(prefixes);

            for (RefreshTreeNode prefix :
                    prefixes) {

                refreshAction.accept(prefix);
            }
        }

        /*
         * executeRefresh() çalışırken yeni bir refresh
         * isteği geldiyse onu kaybetme.
         *
         * Yeni event zaten timer'ı schedule etmiş olabilir.
         */
        if (currentTableRefreshPending
                || !pendingPrefixes.isEmpty()) {

            scheduleTimer();
        }
    }

    public void cancel() {

        timer.stop();

        pendingPrefixes.clear();

        currentTableRefreshPending = false;

        refreshScheduled.set(false);
    }
}