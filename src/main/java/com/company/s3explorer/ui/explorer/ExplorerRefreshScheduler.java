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

    private void scheduleTimer() {

        /*
         * Aynı anda binlerce transfer tamamlanırsa
         * binlerce invokeLater üretme.
         *
         * Yalnızca ilk event EDT'ye timer kurulması
         * için bir task gönderir.
         */
        if (!refreshScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        SwingUtilities.invokeLater(() -> {

            refreshScheduled.set(false);

            timer.restart();
        });
    }

    private void executeRefresh() {

        /*
         * Current File Table
         */
        if (currentTableRefreshPending) {

            currentTableRefreshPending = false;

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
    }

    public void cancel() {

        timer.stop();

        pendingPrefixes.clear();

        currentTableRefreshPending = false;

        refreshScheduled.set(false);
    }
}