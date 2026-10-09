package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferGroupUpdatedEvent;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.transfer.state.TransferStateStore;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.util.S3Util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TransferPanel
        extends JPanel
        implements TransferListener {

    private static final int UI_VISIBLE_LIMIT = 1000;

    private final TransferEventBus eventBus;
    private final TransferManager transferManager;

    private final TransferStateStore stateStore =
            new TransferStateStore(UI_VISIBLE_LIMIT);

    private JButton cancelButton;
    private JButton cancelAllButton;
    private JButton clearButton;

    private TransferTableModel queuedModel;
    private TransferTableModel runningModel;
    private TransferTableModel finishedModel;
    private TransferTableModel allModel;
    
    private JTable queuedTable;
    private JTable runningTable;
    private JTable finishedTable;
    private JTable allTable;

    private JTabbedPane tabs;

    private final TransferGroupStateStore groupStateStore =
            new TransferGroupStateStore();

    private Timer refreshTimer;

    private long lastRenderedStateVersion = -1;

    private boolean refreshingTables = false;

    private final java.util.concurrent.ConcurrentHashMap<
            UUID,
            TransferGroupUpdatedEvent> pendingGroupUpdates =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicBoolean
            groupUpdateRefreshScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    
    public TransferPanel(
            TransferEventBus eventBus,
            TransferManager transferManager) {

        this.eventBus = eventBus;
        this.transferManager = transferManager;

        initialize();
    }

    private void initialize() {

        createModel();
        createComponents();
        layoutComponents();
        registerListeners();

        refreshVisibleTables();
        updateTabTitles();
    }

    private void createModel() {
        queuedModel = new TransferTableModel();
        runningModel = new TransferTableModel();
        finishedModel = new TransferTableModel();
        allModel = new TransferTableModel();
    }

    private void createComponents() {

        cancelButton =
                new JButton();

        cancelButton.setToolTipText(
                "Cancel Selected");

        cancelButton.setPreferredSize(
                new Dimension(
                        30,
                        30));

        cancelAllButton =
                new JButton();

        cancelAllButton.setToolTipText(
                "Cancel All");

        cancelAllButton.setPreferredSize(
                new Dimension(
                        30,
                        30));

        clearButton =
                new JButton();

        clearButton.setToolTipText(
                "Clear Logs");

        clearButton.setPreferredSize(
                new Dimension(
                        30,
                        30));

        cancelButton.setEnabled(false);
        cancelAllButton.setEnabled(false);
        clearButton.setEnabled(false);

        cancelButton.addActionListener(
                e -> cancelSelectedTransfers());

        cancelAllButton.addActionListener(
                e -> cancelAllTransfers());

        clearButton.addActionListener(
                e -> clearFinishedTransfers());

        setButtonIcons();

        /*
         * Queued
         *
         * Queued hâlâ yalnızca TransferRuntime
         * kayıtlarını gösteriyor.
         */
        queuedTable =
                createTable(
                        queuedModel);

        /*
         * Running
         *
         * Group + individual TransferRuntime
         * kayıtlarını birlikte gösterir.
         */
        runningTable =
                createTable(
                        runningModel);

        /*
         * Finished
         *
         * Group + individual TransferRuntime
         * kayıtlarını birlikte gösterir.
         */
        finishedTable =
                createTable(
                        finishedModel);

        /*
         * All
         *
         * Group + individual TransferRuntime
         * kayıtlarını birlikte gösterir.
         */
        allTable =
                createTable(
                        allModel);

        tabs =
                new JTabbedPane();

        /*
         * UI yenilemesi yalnızca son snapshot'ı
         * almak için kullanılıyor.
         *
         * Transfer event'leri burada işlenmiyor.
         */
        refreshTimer =
                new Timer(
                        100,
                        e -> refreshFromStateStore());

        refreshTimer.start();
    }

    private void layoutComponents() {

        JPanel toolbar =
                new JPanel(
                        new FlowLayout());

        toolbar.setPreferredSize(
                new Dimension(
                        50,
                        0));

        toolbar.setBorder(
                BorderFactory.createEmptyBorder(
                        50,
                        5,
                        10,
                        5));

        toolbar.add(cancelButton);
        toolbar.add(cancelAllButton);
        toolbar.add(clearButton);

        tabs.addTab(
                "Queued",
                new JScrollPane(
                        queuedTable));

        tabs.addTab(
                "Running",
                new JScrollPane(
                        runningTable));

        /*
         * Finished
         *
         * Unified table:
         *
         *   [Group rows]
         *   [Individual transfer rows]
         */
        tabs.addTab(
                "Finished",
                new JScrollPane(
                        finishedTable));
        
        tabs.addTab(
                "All",
                new JScrollPane(
                        allTable));

        /*
         * Varsayılan olarak Running sekmesini göster.
         */
        tabs.setSelectedIndex(1);

        JPanel contentPanel =
                new JPanel(
                        new BorderLayout());

        contentPanel.add(
                tabs,
                BorderLayout.CENTER);

        setLayout(
                new BorderLayout());

        add(
                toolbar,
                BorderLayout.WEST);

        add(
                contentPanel,
                BorderLayout.CENTER);
    }

    private void registerListeners() {

        eventBus.subscribe(this);

        registerSelectionListener(
                queuedTable);

        registerSelectionListener(
                runningTable);

        registerSelectionListener(
                finishedTable);

        registerSelectionListener(
                allTable);

        tabs.addChangeListener(
                e -> updateButtons());
    }

    private void registerSelectionListener(
            JTable table) {

        table.getSelectionModel()
                .addListSelectionListener(
                        e -> {

                            if (!e.getValueIsAdjusting()) {
                                updateButtons();
                            }
                        });
    }
    
    /*
     * ÖNEMLİ:
     *
     * Bu metot event thread'inden çağrılabilir.
     *
     * Swing'e dokunmuyoruz.
     *
     * Runtime doğrudan thread-safe StateStore'a giriyor.
     */
    @Override
    public void onTransferUpdated(
            TransferRuntime runtime) {

        if (runtime == null) {
            return;
        }

        stateStore.upsert(runtime);
    }

    @Override
    public void onQueuedTransfersCancelled(
            List<TransferRuntime> runtimes) {

        if (runtimes == null
                || runtimes.isEmpty()) {
            return;
        }

        /*
         * Cancel All sırasında 10.000 / 50.000+
         * runtime'ı tek tek upsert etmiyoruz.
         *
         * StateStore bunları tek bulk transition
         * olarak işliyor.
         */
        stateStore.markAllQueuedCancelled(
                runtimes);
    }
    
    @Override
    public void onTransferGroupUpdated(
            TransferGroupUpdatedEvent event) {

        if (event == null
                || event.getGroup() == null) {
            return;
        }

        UUID groupId =
                event.getGroup().getId();

        /*
         * Aynı group için yalnızca en son update'i tut.
         *
         * Preparing / Running sırasında çok sayıda
         * update gelebilir.
         */
        pendingGroupUpdates.put(
                groupId,
                event);

        /*
         * EDT'de zaten bir group refresh bekliyorsa
         * yeni bir EDT işi oluşturma.
         */
        if (!groupUpdateRefreshScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        SwingUtilities.invokeLater(
                this::processPendingGroupUpdates);
    }


    /*
     * Final logical group completion.
     *
     * ÖNEMLİ:
     *
     * Completion artık doğrudan ayrı bir EDT işi olarak
     * çalıştırılmıyor.
     *
     * Önce pending update'ler işleniyor,
     * ardından completion aynı EDT sırası içinde
     * uygulanıyor.
     */
    @Override
    public void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {

        if (event == null
                || event.getGroup() == null) {
            return;
        }

        UUID groupId =
                event.getGroup().getId();

        /*
         * Completion için bekleyen update'i ezebiliriz.
         *
         * Ancak completion'ın kendisini ayrıca saklamıyoruz.
         * Completion sıralaması ayrı bir kuyruk üzerinden
         * korunuyor.
         */
        SwingUtilities.invokeLater(() -> {

            /*
             * Completion EDT'ye geldiğinde önce aynı group için
             * pending update varsa onu uygula.
             *
             * Böylece:
             *
             *     Updated
             *     Updated
             *     Completed
             *
             * sırası korunur.
             */
            TransferGroupUpdatedEvent pending =
                    pendingGroupUpdates.remove(
                            groupId);

            if (pending != null) {

                groupStateStore.upsert(
                        pending);
            }

            /*
             * Completion her zaman en son uygulanır.
             *
             * Böylece Finished state'i daha eski bir
             * Updated event tarafından ezilemez.
             */
            groupStateStore.complete(
                    event);

            refreshVisibleTables();
            updateTabTitles();
            updateButtons();
        });
    }

    private void processPendingGroupUpdates() {

        try {

            List<TransferGroupUpdatedEvent> updates =
                    new ArrayList<>();

            /*
             * Bekleyen event'leri tek tek güvenli şekilde
             * kuyruktan çıkar.
             *
             * Event bu sırada başka bir thread tarafından
             * güncellenmişse remove(key, value) eski event'i
             * silmez. Yeni event kuyrukta kalır.
             */
            for (UUID groupId : pendingGroupUpdates.keySet()) {

                TransferGroupUpdatedEvent event =
                        pendingGroupUpdates.get(groupId);

                if (event == null) {
                    continue;
                }

                if (pendingGroupUpdates.remove(
                        groupId,
                        event)) {

                    updates.add(event);
                }
            }

            /*
             * Kuyruktan güvenli şekilde alınan event'leri işle.
             */
            for (TransferGroupUpdatedEvent event : updates) {

                if (event == null
                        || event.getGroup() == null) {
                    continue;
                }

                groupStateStore.upsert(event);
            }

            refreshVisibleTables();
            updateTabTitles();
            updateButtons();

        } finally {

            groupUpdateRefreshScheduled.set(false);

            /*
             * İşlem sırasında yeni event geldiyse
             * tekrar tek bir EDT işi oluştur.
             */
            if (!pendingGroupUpdates.isEmpty()
                    && groupUpdateRefreshScheduled.compareAndSet(
                    false,
                    true)) {

                SwingUtilities.invokeLater(
                        this::processPendingGroupUpdates);
            }
        }
    }

    /*
     * Sadece EDT üzerinde çalışır.
     *
     * StateStore'dan en fazla 1000'er kayıt alır.
     */
    private void refreshFromStateStore() {

        long currentVersion =
                stateStore.getVersion();

        /*
         * Transfer state değişmemişse
         * JTable modellerine dokunma.
         */
        if (currentVersion
                == lastRenderedStateVersion) {

            updateTabTitles();

            return;
        }
        
        refreshVisibleTables();

        lastRenderedStateVersion =
                currentVersion;

        updateTabTitles();
        updateButtons();
    }

    private void refreshVisibleTables() {

        if (refreshingTables) {
            return;
        }

        refreshingTables = true;

        try {

            List<String> runningSelection =
                    captureSelection(
                            runningTable,
                            runningModel);

            List<String> finishedSelection =
                    captureSelection(
                            finishedTable,
                            finishedModel);

            List<String> allSelection =
                    captureSelection(
                            allTable,
                            allModel);

            queuedModel.setSnapshot(
                    stateStore.snapshot(
                            TransferStateStore.View.QUEUED));

            runningModel.setSnapshot(
                    groupStateStore.runningSnapshot(),
                    stateStore.snapshot(
                            TransferStateStore.View.RUNNING));

            finishedModel.setSnapshot(
                    groupStateStore.finishedSnapshot(),
                    stateStore.snapshot(
                            TransferStateStore.View.FINISHED));

            allModel.setSnapshot(
                    groupStateStore.snapshot(),
                    stateStore.snapshot(
                            TransferStateStore.View.ALL));

            restoreSelection(
                    runningTable,
                    runningModel,
                    runningSelection);

            restoreSelection(
                    finishedTable,
                    finishedModel,
                    finishedSelection);

            restoreSelection(
                    allTable,
                    allModel,
                    allSelection);

        } finally {

            refreshingTables = false;

            updateButtons();
        }
    }

    /*
     * JTable üzerindeki mevcut seçimleri
     * satırın pozisyonuna göre değil, gerçek kimliğine
     * göre saklar.
     */
    private List<String> captureSelection(
            JTable table,
            TransferTableModel model) {

        List<String> selection =
                new ArrayList<>();

        if (table == null
                || model == null) {

            return selection;
        }

        int[] selectedRows =
                table.getSelectedRows();

        for (int viewRow : selectedRows) {

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            if (model.isGroupRow(modelRow)) {

                TransferGroupStateStore.GroupRecord group =
                        model.getGroup(modelRow);

                if (group == null
                        || group.getGroup() == null
                        || group.getGroup().getId() == null) {

                    continue;
                }

                selection.add(
                        "GROUP:"
                                + group.getGroup().getId());

                continue;
            }

            TransferRuntime runtime =
                    model.getRuntime(modelRow);

            if (runtime == null
                    || runtime.getTask() == null
                    || runtime.getTask().getId() == null) {

                continue;
            }

            selection.add(
                    "TRANSFER:"
                            + runtime.getTask().getId());
        }

        return selection;
    }


    /*
     * Snapshot yenilendikten sonra daha önce seçilmiş
     * Group / Transfer satırlarını tekrar seçer.
     */
    private void restoreSelection(
            JTable table,
            TransferTableModel model,
            List<String> selection) {

        if (table == null
                || model == null
                || selection == null
                || selection.isEmpty()) {

            return;
        }

        table.clearSelection();

        for (int modelRow = 0;
             modelRow < model.getRowCount();
             modelRow++) {

            String key = null;

            if (model.isGroupRow(modelRow)) {

                TransferGroupStateStore.GroupRecord group =
                        model.getGroup(modelRow);

                if (group != null
                        && group.getGroup() != null
                        && group.getGroup().getId() != null) {

                    key =
                            "GROUP:"
                                    + group.getGroup().getId();
                }

            } else {

                TransferRuntime runtime =
                        model.getRuntime(modelRow);

                if (runtime != null
                        && runtime.getTask() != null
                        && runtime.getTask().getId() != null) {

                    key =
                            "TRANSFER:"
                                    + runtime.getTask().getId();
                }
            }

            if (key == null
                    || !selection.contains(key)) {

                continue;
            }

            int viewRow =
                    table.convertRowIndexToView(
                            modelRow);

            if (viewRow >= 0) {

                table.addRowSelectionInterval(
                        viewRow,
                        viewRow);
            }
        }
    }

    private JTable createTable(
            TransferTableModel model) {

        JTable table =
                new JTable(model) {

                    @Override
                    public Component prepareRenderer(
                            javax.swing.table.TableCellRenderer renderer,
                            int row,
                            int column) {

                        Component component =
                                super.prepareRenderer(
                                        renderer,
                                        row,
                                        column);

                        boolean groupRow =
                                model.isGroupRow(row);

                        boolean selected =
                                isRowSelected(row);

                        if (selected) {
                            return component;
                        }

                        if (groupRow) {

                            component.setBackground(
                                    createGroupBackground(
                                            getBackground()));

                        } else {

                            component.setBackground(
                                    getBackground());
                        }

                        return component;
                    }

                    private Color createGroupBackground(
                            Color base) {

                        if (base == null) {
                            return null;
                        }

                        float[] hsb =
                                Color.RGBtoHSB(
                                        base.getRed(),
                                        base.getGreen(),
                                        base.getBlue(),
                                        null);

                        if (hsb[2] < 0.5f) {

                            return new Color(
                                    0,
                                    91,
                                    130);

                        } else {

                            return new Color(
                                    36,
                                    200,
                                    255);
                        }
                    }
                };

        configureTable(
                table,
                100);

        table.getColumnModel()
                .getColumn(0)
                .setCellRenderer(
                        new TypeRenderer());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(
                        new FileSizeRenderer());

        table.getColumnModel()
                .getColumn(3)
                .setCellRenderer(
                        new TransferProgressRenderer());

        table.getColumnModel()
                .getColumn(4)
                .setCellRenderer(
                        new StatusRenderer());

        table.getColumnModel()
                .getColumn(6)
                .setCellRenderer(
                        new LongFormatRenderer());

        return table;
    }

    private void updateTabTitles() {

        long queued =
                stateStore.getQueuedCount();

        /*
         * Running:
         *
         * Tablo en fazla 1000 kayıt gösterir.
         * Tab başlığında ise gerçek toplam sayı gösterilir.
         *
         * Group + individual transfer kayıtları birlikte sayılır.
         */
        long runningGroups =
                groupStateStore.runningSnapshot().size();

        long runningTransfers =
                stateStore.getRunningCount();

        long running =
                runningGroups
                        + runningTransfers;

        /*
         * Finished:
         *
         * Tablo en fazla 1000 kayıt gösterir.
         * Tab başlığında gerçek toplam sayı gösterilir.
         *
         * Group + individual transfer kayıtları birlikte sayılır.
         */
        long finishedGroups =
                groupStateStore.finishedSnapshot().size();

        long finishedTransfers =
                stateStore.getFinishedCount();

        long finished =
                finishedGroups
                        + finishedTransfers;

        /*
         * All:
         *
         * All tabında da gerçek toplam sayı gösterilir.
         */
        long allGroups =
                groupStateStore.snapshot().size();

        long allTransfers =
                stateStore.getTotalCount();

        long all =
                allGroups
                        + allTransfers;

        tabs.setTitleAt(
                0,
                "Queued (" + S3Util.formatWithThousandSeparator(queued) + ")");

        tabs.setTitleAt(
                1,
                "Running (" + S3Util.formatWithThousandSeparator(running) + ")");

        tabs.setTitleAt(
                2,
                "Finished (" + S3Util.formatWithThousandSeparator(finished) + ")");

        tabs.setTitleAt(
                3,
                "All (" + S3Util.formatWithThousandSeparator(all) + ")");
    }

    private void updateButtons() {

        /*
         * JTable snapshot refresh'i sırasında
         * selection geçici olarak boşalabilir.
         *
         * Bu sırada butonların enabled durumunu
         * değiştirmiyoruz.
         *
         * Refresh tamamlandığında refreshVisibleTables()
         * zaten updateButtons() çağıracaktır.
         */
        if (refreshingTables) {
            return;
        }

        JTable table =
                getSelectedTable();

        boolean cancelEnabled =
                hasCancelableSelection(
                        table);

        boolean hasActive =
                stateStore.getQueuedCount() > 0
                        || stateStore.getRunningCount() > 0;

        boolean cancelAllEnabled =
                hasActive;

        boolean hasFinished =
                stateStore.getFinishedCount() > 0
                        || !groupStateStore
                        .finishedSnapshot()
                        .isEmpty();

        boolean clearEnabled =
                hasFinished;

        cancelButton.setEnabled(
                cancelEnabled);

        cancelAllButton.setEnabled(
                cancelAllEnabled);

        clearButton.setEnabled(
                clearEnabled);
    }

    private JTable getSelectedTable() {

        int index =
                tabs.getSelectedIndex();

        return switch (index) {

            case 0 ->
                    queuedTable;

            case 1 ->
                    runningTable;

            case 2 ->
                    finishedTable;

            case 3 ->
                    allTable;

            default ->
                    null;
        };
    }


    private void cancelSelectedTransfers() {

        JTable table = getSelectedTable();

        if (table == null) {
            return;
        }

        int[] selectedRows = table.getSelectedRows();

        if (selectedRows.length == 0) {
            return;
        }

        TransferTableModel model = getModelForTable(table);

        if (model == null) {
            return;
        }

        boolean groupCancellationAllowed =
                table == runningTable || table == allTable;

        for (int viewRow : selectedRows) {

            int modelRow =
                    table.convertRowIndexToModel(viewRow);

            /*
             * GROUP
             */
            if (model.isGroupRow(modelRow)) {

                if (!groupCancellationAllowed) {
                    continue;
                }

                TransferGroupStateStore.GroupRecord group =
                        model.getGroup(modelRow);

                if (group == null
                        || group.getGroup() == null
                        || group.getGroup().getId() == null) {
                    continue;
                }

                TransferGroup transferGroup = group.getGroup();

                if (transferGroup.isFinished()) {
                    continue;
                }

                transferManager.cancelGroup(transferGroup);
                continue;
            }

            /*
             * NORMAL TRANSFER
             */
            TransferRuntime runtime = model.getRuntime(modelRow);

            if (runtime == null
                    || runtime.getTask() == null
                    || runtime.getTask().getId() == null) {
                continue;
            }

            if (runtime.getStatus().isActive()) {
                transferManager.cancel(runtime.getTask().getId());
            }
        }
    }

    private void cancelAllTransfers() {

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        "Cancel all transfers?",
                        "Cancelling All Transfers",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        cancelAllButton.setEnabled(false);

        transferManager.cancelAll();
    }

    private void clearFinishedTransfers() {

        if (stateStore.getFinishedCount() <= 0
                && groupStateStore.finishedSnapshot().isEmpty()) {

            return;
        }

        clearButton.setEnabled(false);

        CompletableFuture
                .runAsync(
                        stateStore::removeFinished)
                .whenComplete(
                        (ignored, error) -> {

                            SwingUtilities.invokeLater(() -> {

                                if (error != null) {

                                    clearButton.setEnabled(
                                            true);

                                    error.printStackTrace();

                                    JOptionPane.showMessageDialog(
                                            this,
                                            "Logs could not be cleared:\n"
                                                    + error.getMessage(),
                                            "Clear Logs",
                                            JOptionPane.ERROR_MESSAGE);

                                    return;
                                }

                                /*
                                 * Finished task kayıtları ile birlikte
                                 * Finished logical group kayıtlarını da temizle.
                                 *
                                 * Running gruplara dokunma.
                                 */
                                groupStateStore.removeFinished();

                                refreshVisibleTables();

                                lastRenderedStateVersion =
                                        stateStore.getVersion();

                                updateTabTitles();
                                updateButtons();
                            });
                        });
    }


    private boolean hasCancelableSelection(JTable table) {

        if (table == null) {
            return false;
        }

        int[] selectedRows = table.getSelectedRows();

        if (selectedRows.length == 0) {
            return false;
        }

        TransferTableModel model = getModelForTable(table);

        if (model == null) {
            return false;
        }

        boolean groupCancellationAllowed =
                table == runningTable || table == allTable;

        for (int viewRow : selectedRows) {

            int modelRow =
                    table.convertRowIndexToModel(viewRow);

            /*
             * GROUP
             */
            if (model.isGroupRow(modelRow)) {

                if (!groupCancellationAllowed) {
                    continue;
                }

                TransferGroupStateStore.GroupRecord group =
                        model.getGroup(modelRow);

                if (group != null
                        && group.getGroup() != null
                        && group.getGroup().getId() != null
                        && !group.getGroup().isFinished()) {
                    return true;
                }

                continue;
            }

            /*
             * NORMAL TRANSFER
             */
            TransferRuntime runtime = model.getRuntime(modelRow);

            if (runtime != null
                    && runtime.getTask() != null
                    && runtime.getTask().getId() != null
                    && runtime.getStatus().isActive()) {
                return true;
            }
        }

        return false;
    }


    private TransferTableModel getModelForTable(JTable table) {

        if (table == queuedTable) {
            return queuedModel;
        }

        if (table == runningTable) {
            return runningModel;
        }

        if (table == finishedTable) {
            return finishedModel;
        }

        if (table == allTable) {
            return allModel;
        }

        return null;
    }

    public void setButtonIcons() {

        cancelButton.setIcon(
                IconProvider.ICON_CANCEL);

        cancelAllButton.setIcon(
                IconProvider.ICON_CANCEL_ALL);

        clearButton.setIcon(
                IconProvider.ICON_DELETE);
    }

    private void configureTable(
            JTable table,
            int progressColumnWidth) {

        table.setRowHeight(54);

        table.setAutoCreateRowSorter(false);

        table.setRowSorter(null);

        table.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(600);

        table.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(3)
                .setPreferredWidth(
                        progressColumnWidth);

        table.getColumnModel()
                .getColumn(4)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(5)
                .setPreferredWidth(50);

        table.getColumnModel()
                .getColumn(6)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(7)
                .setPreferredWidth(300);

        table.getTableHeader()
                .setReorderingAllowed(false);
    }
}