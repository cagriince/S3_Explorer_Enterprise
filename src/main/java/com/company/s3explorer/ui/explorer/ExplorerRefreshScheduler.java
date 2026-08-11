package com.company.s3explorer.ui.explorer;

import javax.swing.Timer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ExplorerRefreshScheduler {

    private final Timer timer;
    private Consumer<RefreshTreeNode> refreshAction;
    private final Set<RefreshTreeNode> pendingPrefixes = new HashSet<>();

    public ExplorerRefreshScheduler(Consumer<RefreshTreeNode> refreshAction) {
        this.refreshAction = refreshAction;
        timer = new Timer(500, e -> {
            for (RefreshTreeNode prefix : pendingPrefixes) {
                refreshAction.accept(prefix);
            }
            pendingPrefixes.clear();
        });

        timer.setRepeats(false);
    }

    public void scheduleRefresh(Collection<RefreshTreeNode> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return;
        }

        pendingPrefixes.addAll(prefixes);
        timer.restart();
    }

    public void cancel() {
        timer.stop();
    }
}
