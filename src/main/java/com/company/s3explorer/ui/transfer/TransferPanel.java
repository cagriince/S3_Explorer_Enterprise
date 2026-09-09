package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferGroupUpdatedEvent;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.transfer.state.TransferStateStore;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.theme.UIThemeManager;

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
    private TransferCombinedTableModel runningModel;
    private TransferCombinedTableModel finishedModel;
    private TransferCombinedTableModel allModel;
    
    private JTable queuedTable;
    private JTable runningTable;
    private JTable finishedTable;
    private JTable allTable;

    private JTabbedPane tabs;

    private final TransferGroupStateStore groupStateStore =
            new TransferGroupStateStore();

    private Timer refreshTimer;

    private long lastRenderedStateVersion = -1;

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

        queuedModel =
                new TransferTableModel(
                        UI_VISIBLE_LIMIT);

        runningModel =
                new TransferCombinedTableModel(
                        UI_VISIBLE_LIMIT);

        finishedModel =
                new TransferCombinedTableModel(
                        UI_VISIBLE_LIMIT);

        allModel =
                new TransferCombinedTableModel(
                        UI_VISIBLE_LIMIT);
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
                createCombinedTable(
                        runningModel);

        /*
         * Finished
         *
         * Group + individual TransferRuntime
         * kayıtlarını birlikte gösterir.
         */
        finishedTable =
                createCombinedTable(
                        finishedModel);

        /*
         * All
         *
         * Group + individual TransferRuntime
         * kayıtlarını birlikte gösterir.
         */
        allTable =
                createCombinedTable(
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
    public void onTransfersUpdated(
            List<TransferRuntime> runtimes) {

        if (runtimes == null
                || runtimes.isEmpty()) {
            return;
        }

        stateStore.upsertAll(runtimes);
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
         * Aynı group için yalnızca en son event'i tut.
         *
         * Preparing sırasında çok sayıda update gelebilir.
         * Böylece EDT kuyruğuna her event için ayrı iş
         * eklenmez.
         */
        pendingGroupUpdates.put(
                groupId,
                event);

        /*
         * EDT'de zaten bir refresh bekliyorsa
         * yeni bir refresh schedule etme.
         */
        if (!groupUpdateRefreshScheduled.compareAndSet(
                false,
                true)) {

            return;
        }

        SwingUtilities.invokeLater(
                this::processPendingGroupUpdates);
    }

    private void processPendingGroupUpdates() {

        try {

            List<TransferGroupUpdatedEvent> updates =
                    new ArrayList<>(
                            pendingGroupUpdates.values());

            pendingGroupUpdates.clear();

            for (TransferGroupUpdatedEvent event :
                    updates) {

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
             * Bu işlem sırasında yeni event geldiyse,
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
     * Final logical group completion.
     *
     * Individual task events continue to use StateStore.
     * This callback is responsible only for the group-level
     * final result shown in the Finished tab.
     */
    @Override
    public void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {

        if (event == null || event.getGroup() == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            groupStateStore.complete(event);
            
            refreshVisibleTables();
            updateTabTitles();
            updateButtons();
        });
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
            updateButtons();

            return;
        }

        refreshVisibleTables();

        lastRenderedStateVersion =
                currentVersion;

        updateTabTitles();
        updateButtons();
    }

    private void refreshVisibleTables() {

        /*
         * Queued yalnızca individual transfer task'larını
         * göstermeye devam eder.
         */
        queuedModel.setSnapshot(
                stateStore.snapshot(
                        TransferStateStore.View.QUEUED));

        /*
         * Running:
         *
         *     [Group rows]
         *     [Individual transfer rows]
         */
        runningModel.setSnapshot(
                groupStateStore.runningSnapshot(),
                stateStore.snapshot(
                        TransferStateStore.View.RUNNING));

        /*
         * Finished:
         *
         *     [Group rows]
         *     [Individual transfer rows]
         */
        finishedModel.setSnapshot(
                groupStateStore.finishedSnapshot(),
                stateStore.snapshot(
                        TransferStateStore.View.FINISHED));

        /*
         * All:
         *
         *     [All group rows]
         *     [All individual transfer rows]
         */
        allModel.setSnapshot(
                groupStateStore.snapshot(),
                stateStore.snapshot(
                        TransferStateStore.View.ALL));

    }

    private JTable createTable(
            TransferTableModel model) {

        JTable table =
                new JTable(model);

        configureTable(
                table,
                100);

        /*
         * Normal Transfer Table renderer'ları.
         */
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
                        new ProgressBarRenderer());

        table.getColumnModel()
                .getColumn(4)
                .setCellRenderer(
                        new StatusRenderer());

        table.getColumnModel()
                .getColumn(5)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(6)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(7)
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
         * Unified Running tab'ında:
         *
         *   [Group rows]
         *   [Individual transfer rows]
         *
         * gösteriliyor.
         *
         * Bu nedenle tab sayısı da görünür satır
         * sayısıyla aynı olmalı.
         */
        long runningGroups =
                groupStateStore.runningSnapshot().size();

        long runningTransfers =
                stateStore.snapshot(
                                TransferStateStore.View.RUNNING)
                        .size();

        long running =
                runningGroups
                        + runningTransfers;

        /*
         * Finished:
         *
         * Unified Finished tab'ında:
         *
         *   [Group rows]
         *   [Individual transfer rows]
         *
         * gösteriliyor.
         */
        long finishedGroups =
                groupStateStore.finishedSnapshot().size();

        long finishedTransfers =
                stateStore.snapshot(
                                TransferStateStore.View.FINISHED)
                        .size();

        long finished =
                finishedGroups
                        + finishedTransfers;

        /*
         * All:
         *
         * Unified All tab'ında:
         *
         *   [All group rows]
         *   [All individual transfer rows]
         *
         * gösteriliyor.
         */
        long allGroups =
                groupStateStore.snapshot().size();

        long allTransfers =
                stateStore.snapshot(
                                TransferStateStore.View.ALL)
                        .size();

        long total =
                allGroups
                        + allTransfers;

        tabs.setTitleAt(
                0,
                "Queued (" + queued + ")");

        tabs.setTitleAt(
                1,
                "Running (" + running + ")");

        tabs.setTitleAt(
                2,
                "Finished (" + finished + ")");

        tabs.setTitleAt(
                3,
                "All (" + total + ")");

        /*
         * Cancel All task seviyesinde çalışmaya devam ediyor.
         */
        cancelAllButton.setEnabled(
                stateStore.getQueuedCount() > 0
                        || stateStore.getRunningCount() > 0);

        /*
         * Clear Logs hem task hem group kayıtlarını
         * dikkate almalı.
         */
        clearButton.setEnabled(
                stateStore.getFinishedCount() > 0
                        || !groupStateStore.finishedSnapshot().isEmpty());
    }
    
    private void updateButtons() {

        JTable table =
                getSelectedTable();

        cancelButton.setEnabled(
                hasCancelableSelection(
                        table));

        boolean hasActive =
                stateStore.getQueuedCount() > 0
                        || stateStore.getRunningCount() > 0;

        cancelAllButton.setEnabled(
                hasActive);
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

    private TransferTableModel getModelForTable(
            JTable table) {

        if (table == queuedTable) {
            return queuedModel;
        }

        /*
         * Combined tabloların kendi TransferRuntime
         * modeline doğrudan erişmiyoruz.
         *
         * Bir sonraki adımda combined model üzerinden
         * seçilen satırın gerçek TransferRuntime'ını
         * çözeceğiz.
         */
        return null;
    }
    
    private void cancelSelectedTransfers() {

        JTable table =
                getSelectedTable();

        if (table == null) {
            return;
        }

        int[] selectedRows =
                table.getSelectedRows();

        if (selectedRows.length == 0) {
            return;
        }

        /*
         * Queued tablosu klasik TransferTableModel kullanıyor.
         */
        if (table == queuedTable) {

            for (int viewRow :
                    selectedRows) {

                /*
                 * Sorter olmadığı için
                 * view row == model row.
                 */
                int modelRow =
                        viewRow;

                TransferRuntime runtime =
                        queuedModel.getRuntimeAtModelRow(
                                modelRow);

                if (runtime == null) {
                    continue;
                }

                if (runtime.getStatus().isActive()) {

                    transferManager.cancel(
                            runtime.getTask().getId());
                }
            }

            return;
        }

        /*
         * Running / Finished / All tabloları
         * TransferCombinedTableModel kullanıyor.
         *
         * Group satırları burada bilinçli olarak
         * atlanır.
         */
        TransferCombinedTableModel combinedModel =
                getCombinedModelForTable(table);

        if (combinedModel == null) {
            return;
        }

        for (int viewRow :
                selectedRows) {

            /*
             * Sorter olmadığı için
             * view row == model row.
             */
            int modelRow =
                    viewRow;

            if (combinedModel.isGroupRow(
                    modelRow)) {

                continue;
            }

            TransferRuntime runtime =
                    combinedModel.getRuntime(
                            modelRow);

            if (runtime == null) {
                continue;
            }

            if (runtime.getStatus().isActive()) {

                transferManager.cancel(
                        runtime.getTask().getId());
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

    private boolean hasCancelableSelection(
            JTable table) {

        if (table == null) {
            return false;
        }

        int[] selectedRows =
                table.getSelectedRows();

        if (selectedRows.length == 0) {
            return false;
        }

        /*
         * Queued
         */
        if (table == queuedTable) {

            for (int row :
                    selectedRows) {

                TransferRuntime runtime =
                        queuedModel.getRuntimeAtModelRow(
                                row);

                if (runtime != null
                        && runtime.getStatus().isActive()) {

                    return true;
                }
            }

            return false;
        }

        /*
         * Running / Finished / All
         */
        TransferCombinedTableModel combinedModel =
                getCombinedModelForTable(table);

        if (combinedModel == null) {
            return false;
        }

        for (int row :
                selectedRows) {

            /*
             * Group satırları hiçbir zaman
             * Cancel Selected için uygun değil.
             */
            if (combinedModel.isGroupRow(row)) {
                continue;
            }

            TransferRuntime runtime =
                    combinedModel.getRuntime(row);

            if (runtime != null
                    && runtime.getStatus().isActive()) {

                return true;
            }
        }

        return false;
    }

    private TransferCombinedTableModel getCombinedModelForTable(
            JTable table) {

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

    private JTable createCombinedTable(
            TransferCombinedTableModel model) {

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

                        /*
                         * Combined model'de sorter olmadığı için
                         * view row == model row.
                         */
                        boolean groupRow =
                                model.isGroupRow(row);

                        boolean selected =
                                isRowSelected(row);

                        /*
                         * Seçili satırda Swing'in mevcut
                         * selection renklerini kesinlikle bozma.
                         */
                        if (selected) {
                            return component;
                        }

                        if (groupRow) {

                            /*
                             * Tema bağımsız grup satırı görünümü.
                             *
                             * Sabit RGB kullanmıyoruz.
                             * Mevcut JTable background renginden
                             * hafif bir varyasyon üretiyoruz.
                             */
                            Color base =
                                    getBackground();

                            Color groupBackground =
                                    createGroupBackground(
                                            base);

                            component.setBackground(
                                    groupBackground);

                        } else {

                            /*
                             * Normal transfer satırı:
                             *
                             * JTable'ın mevcut tema görünümü.
                             */
                            component.setBackground(
                                    getBackground());
                        }

                        /*
                         * ÖNEMLİ:
                         *
                         * Burada fontu değiştirmiyoruz.
                         *
                         * Grup adı zaten S3Util tarafından
                         * kendi içinde bold olarak oluşturuluyor.
                         *
                         * Böylece Process Detail genişleyip
                         * satırın aşağı taşmasına neden olmuyor.
                         */

                        return component;
                    }

                    private Color createGroupBackground(
                            Color base) {

                        if (base == null) {
                            return null;
                        }

                        /*
                         * Mevcut tema background renginden
                         * hafif bir varyasyon üret.
                         *
                         * Dark theme:
                         *     biraz aydınlat.
                         *
                         * Light theme:
                         *     biraz koyulaştır.
                         */
                        float[] hsb =
                                Color.RGBtoHSB(
                                        base.getRed(),
                                        base.getGreen(),
                                        base.getBlue(),
                                        null);

                        float brightness =
                                hsb[2];

                        float saturation =
                                hsb[1];

                        float newBrightness;

                        if (brightness < 0.5f) {

                            /*
                             * Dark theme
                             */
                            newBrightness =
                                    Math.min(
                                            1.0f,
                                            brightness + 0.08f);

                        } else {

                            /*
                             * Light theme
                             */
                            newBrightness =
                                    Math.max(
                                            0.0f,
                                            brightness - 0.04f);
                        }

                        return Color.getHSBColor(
                                hsb[0],
                                saturation,
                                newBrightness);
                    }
                };

        /*
         * Combined table kolon genişlikleri.
         */
        configureTable(
                table,
                120);

        /*
         * Combined renderer'lar.
         */
        table.getColumnModel()
                .getColumn(0)
                .setCellRenderer(
                        new CombinedTypeRenderer());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(
                        new CombinedFileSizeRenderer());

        table.getColumnModel()
                .getColumn(3)
                .setCellRenderer(
                        new CombinedProgressRenderer());

        table.getColumnModel()
                .getColumn(4)
                .setCellRenderer(
                        new CombinedStatusRenderer());

        table.getColumnModel()
                .getColumn(5)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(6)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(7)
                .setCellRenderer(
                        new LongFormatRenderer());

        return table;
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
                .setPreferredWidth(500);

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
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(6)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(7)
                .setPreferredWidth(1);

        table.getColumnModel()
                .getColumn(8)
                .setPreferredWidth(1);

        table.getTableHeader()
                .setReorderingAllowed(false);
    }
}