package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferGroupUpdatedEvent;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.producer.ProducerRuntime;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.transfer.state.TransferStateStore;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

public class TransferPanel
        extends JPanel
        implements TransferListener {

    private static final int UI_VISIBLE_LIMIT = 1000;

    private static final int GROUP_RESULT_VISIBLE_LIMIT = 100;

    private static final DateTimeFormatter GROUP_RESULT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

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

    private ProducerTableModel producerTableModel;

    private JTable producerTable;

    private JTable queuedTable;
    private JTable runningTable;
    private JTable finishedTable;
    private JTable allTable;

    private JTabbedPane tabs;

    /*
     * Group-level completion results.
     *
     * These are intentionally kept separate from TransferStateStore.
     * TransferStateStore represents individual transfer tasks.
     * Group results represent the final result of a logical
     * multi-item operation.
     */
    private DefaultListModel<GroupResult> groupResultModel;
    private JList<GroupResult> groupResultList;
    private JTextArea groupResultDetails;
    private JPanel groupResultsPanel;

    private final TransferGroupStateStore groupStateStore =
            new TransferGroupStateStore();

    private TransferGroupTableModel runningGroupModel;
    private TransferGroupTableModel finishedGroupModel;

    private JTable runningGroupTable;
    private JTable finishedGroupTable;
    
    private volatile ProducerRuntime pendingProducerUpdate;
    private Timer refreshTimer;

    private long lastRenderedStateVersion = -1;

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
        refreshGroupTables();
        updateTabTitles();
    }

    private void createModel() {

        queuedModel =
                new TransferTableModel(
                        UI_VISIBLE_LIMIT);

        runningModel =
                new TransferTableModel(
                        UI_VISIBLE_LIMIT);

        finishedModel =
                new TransferTableModel(
                        UI_VISIBLE_LIMIT);

        allModel =
                new TransferTableModel(
                        UI_VISIBLE_LIMIT);

        producerTableModel =
                new ProducerTableModel();

        groupResultModel =
                new DefaultListModel<>();

        runningGroupModel =
                new TransferGroupTableModel();

        finishedGroupModel =
                new TransferGroupTableModel();
    }

    private void createComponents() {

        cancelButton =
                new JButton();

        cancelButton.setToolTipText(
                "Cancel Selected");

        cancelButton.setPreferredSize(
                new Dimension(30, 30));

        cancelAllButton =
                new JButton();

        cancelAllButton.setToolTipText(
                "Cancel All");

        cancelAllButton.setPreferredSize(
                new Dimension(30, 30));

        clearButton =
                new JButton();

        clearButton.setToolTipText(
                "Clear Logs");

        clearButton.setPreferredSize(
                new Dimension(30, 30));

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

        producerTable =
                createProducerTable();

        queuedTable =
                createTable(
                        queuedModel);

        runningTable =
                createTable(
                        runningModel);

        finishedTable =
                createTable(
                        finishedModel);

        runningGroupTable =
                createGroupTable(runningGroupModel);

        finishedGroupTable =
                createGroupTable(finishedGroupModel);
        
        allTable =
                createTable(
                        allModel);

        createGroupResultsComponents();

        tabs =
                new JTabbedPane();

        /*
         * UI yenilemesi yalnızca son snapshot'ı almak için
         * kullanılıyor.
         *
         * Transfer event'leri burada işlenmiyor.
         */
        refreshTimer =
                new Timer(
                        100,
                        e -> refreshFromStateStore());

        refreshTimer.start();
    }

    private void createGroupResultsComponents() {

        groupResultList =
                new JList<>(
                        groupResultModel);

        groupResultList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);

        groupResultList.setFixedCellHeight(42);

        groupResultList.setVisibleRowCount(3);

        groupResultList.setCellRenderer(
                new GroupResultRenderer());

        groupResultDetails =
                new JTextArea();

        groupResultDetails.setEditable(false);
        groupResultDetails.setFocusable(false);
        groupResultDetails.setLineWrap(true);
        groupResultDetails.setWrapStyleWord(true);

        groupResultDetails.setRows(4);

        groupResultDetails.setBorder(
                new EmptyBorder(
                        6,
                        8,
                        6,
                        8));

        groupResultDetails.setText(
                "Select a group result to view details.");

        groupResultList.addListSelectionListener(
                e -> {

                    if (!e.getValueIsAdjusting()) {
                        updateGroupResultDetails();
                    }
                });

        JPanel header =
                new JPanel(
                        new BorderLayout());

        JLabel title =
                new JLabel(
                        "Group Results");

        title.setBorder(
                new EmptyBorder(
                        4,
                        6,
                        4,
                        6));

        header.add(
                title,
                BorderLayout.WEST);

        JPanel listPanel =
                new JPanel(
                        new BorderLayout());

        listPanel.add(
                new JScrollPane(
                        groupResultList),
                BorderLayout.CENTER);

        JScrollPane detailsScrollPane =
                new JScrollPane(
                        groupResultDetails);

        detailsScrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        detailsScrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        groupResultsPanel =
                new JPanel(
                        new BorderLayout());

        groupResultsPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        4,
                        4,
                        4,
                        4));

        groupResultsPanel.add(
                header,
                BorderLayout.NORTH);

        groupResultsPanel.add(
                listPanel,
                BorderLayout.CENTER);

        groupResultsPanel.add(
                detailsScrollPane,
                BorderLayout.SOUTH);

        groupResultsPanel.setPreferredSize(
                new Dimension(
                        0,
                        190));
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
                        runningGroupTable));

        /*
         * Finished tab contains two logically different areas:
         *
         * 1. Group Results
         *    Final result of a logical multi-item operation.
         *
         * 2. Individual transfers
         *    Existing task-level finished transfer table.
         */
        tabs.addTab(
                "Finished",
                new JScrollPane(
                        finishedGroupTable));
        
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
    public void onProducerUpdated(
            ProducerRuntime runtime) {

        pendingProducerUpdate = runtime;
    }

    @Override
    public void onTransferGroupUpdated(
            TransferGroupUpdatedEvent event) {

        if (event == null || event.getGroup() == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            groupStateStore.upsert(event);

            refreshGroupTables();
        });
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

            refreshGroupTables();
        });
    }
    
    private void addGroupResult(
            TransferGroupCompletedEvent event) {

        TransferGroup group =
                event.getGroup();

        if (group == null) {
            return;
        }

        GroupResult result =
                new GroupResult(
                        group,
                        event);

        /*
         * Newest result appears first.
         */
        groupResultModel.add(
                0,
                result);

        /*
         * Keep the result list bounded independently
         * from individual transfer task history.
         */
        while (groupResultModel.size()
                > GROUP_RESULT_VISIBLE_LIMIT) {

            groupResultModel.remove(
                    groupResultModel.size() - 1);
        }

        groupResultList.setSelectedIndex(0);

        updateGroupResultDetails();

        groupResultList.revalidate();
        groupResultList.repaint();

        groupResultsPanel.revalidate();
        groupResultsPanel.repaint();
    }

    private void updateGroupResultDetails() {

        GroupResult result =
                groupResultList.getSelectedValue();

        if (result == null) {

            groupResultDetails.setText(
                    "Select a group result to view details.");

            return;
        }

        groupResultDetails.setText(
                result.createDetails());
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

            ProducerRuntime producer =
                    pendingProducerUpdate;

            if (producer != null) {

                pendingProducerUpdate = null;

                producerTableModel.update(
                        producer);

                updateProducerVisibility(
                        producer);
            }

            updateTabTitles();
            updateButtons();

            return;
        }

        refreshVisibleTables();

        lastRenderedStateVersion =
                currentVersion;

        ProducerRuntime producer =
                pendingProducerUpdate;

        if (producer != null) {

            pendingProducerUpdate = null;

            producerTableModel.update(
                    producer);

            updateProducerVisibility(
                    producer);
        }

        updateTabTitles();
        updateButtons();
    }

    private void refreshVisibleTables() {

        queuedModel.setSnapshot(
                stateStore.snapshot(
                        TransferStateStore.View.QUEUED));

        runningModel.setSnapshot(
                stateStore.snapshot(
                        TransferStateStore.View.RUNNING));

        finishedModel.setSnapshot(
                stateStore.snapshot(
                        TransferStateStore.View.FINISHED));

        allModel.setSnapshot(
                stateStore.snapshot(
                        TransferStateStore.View.ALL));
    }

    private void updateProducerVisibility(
            ProducerRuntime runtime) {

        if (runtime == null) {

            producerTable.setVisible(false);

            return;
        }

        producerTable.setVisible(true);

        if (runtime.getStatus().isFinished()) {

            Timer timer =
                    new Timer(
                            3000,
                            e -> {

                                producerTableModel.clear();

                                producerTable.setVisible(
                                        false);

                                producerTable.revalidate();
                                producerTable.repaint();
                            });

            timer.setRepeats(false);
            timer.start();
        }

        producerTable.revalidate();
        producerTable.repaint();
    }

    private JTable createProducerTable() {

        JTable table =
                new JTable(
                        producerTableModel);

        table.setRowHeight(32);
        table.setFocusable(false);
        table.setEnabled(false);

        table.getTableHeader()
                .setReorderingAllowed(false);

        table.setTableHeader(null);

        table.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(500);

        table.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(180);

        table.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(100);

        table.getColumnModel()
                .getColumn(3)
                .setPreferredWidth(160);

        table.getColumnModel()
                .getColumn(4)
                .setPreferredWidth(160);

        table.getColumnModel()
                .getColumn(5)
                .setPreferredWidth(120);

        table.getColumnModel()
                .getColumn(6)
                .setPreferredWidth(300);

        table.getColumnModel()
                .getColumn(1)
                .setCellRenderer(
                        new ProducerProgressRenderer());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(
                        new StatusRenderer());

        table.getColumnModel()
                .getColumn(3)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(4)
                .setCellRenderer(
                        new InstantRenderer());

        table.getColumnModel()
                .getColumn(5)
                .setCellRenderer(
                        new LongFormatRenderer());

        table.getColumnModel()
                .getColumn(6)
                .setCellRenderer(
                        new DefaultTableCellRenderer());

        table.setVisible(false);

        return table;
    }

    private JTable createTable(
            TransferTableModel model) {

        JTable table =
                new JTable(model);

        /*
         * Artık TableRowSorter YOK.
         *
         * Model zaten yalnızca ilgili görünümün
         * kayıtlarını içeriyor.
         */

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
                .setPreferredWidth(100);

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

        table.getTableHeader()
                .setReorderingAllowed(false);

        return table;
    }

    private void updateTabTitles() {

        long queued =
                stateStore.getQueuedCount();

        /*
         * Running ve Finished artık task değil,
         * logical group gösteriyor.
         */
        long running =
                groupStateStore.runningSnapshot().size();

        long finished =
                groupStateStore.finishedSnapshot().size();

        long total =
                stateStore.getTotalCount();

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
                        || !groupStateStore.finishedSnapshot().isEmpty()
                        || !groupResultModel.isEmpty());
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
                    null;

            case 2 ->
                    null;

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

        if (table == allTable) {
            return allModel;
        }

        return null;
    }
    
    private void cancelSelectedTransfers() {

        JTable table =
                getSelectedTable();

        if (table == null) {
            return;
        }

        TransferTableModel model =
                getModelForTable(table);

        if (model == null) {
            return;
        }

        int[] selectedRows =
                table.getSelectedRows();

        if (selectedRows.length == 0) {
            return;
        }

        for (int viewRow :
                selectedRows) {

            /*
             * Artık sorter olmadığı için
             * view row == model row.
             */
            int modelRow =
                    viewRow;

            TransferRuntime runtime =
                    model.getRuntimeAtModelRow(
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
                && groupResultModel.isEmpty()) {

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
                                 * Clear group-level final results
                                 * together with finished task logs.
                                 */
                                groupResultModel.clear();

                                groupResultDetails.setText(
                                        "Select a group result to view details.");

                                refreshVisibleTables();

                                lastRenderedStateVersion =
                                        stateStore.getVersion();

                                updateTabTitles();
                                updateButtons();

                                groupResultList.revalidate();
                                groupResultList.repaint();
                            });
                        });
    }

    private boolean hasCancelableSelection(
            JTable table) {

        if (table == null) {
            return false;
        }

        TransferTableModel model =
                getModelForTable(table);

        if (model == null) {
            return false;
        }

        for (int row :
                table.getSelectedRows()) {

            TransferRuntime runtime =
                    model.getRuntimeAtModelRow(
                            row);

            if (runtime != null
                    && runtime.getStatus().isActive()) {

                return true;
            }
        }

        return false;
    }

    public void setButtonIcons() {

        cancelButton.setIcon(
                IconProvider.ICON_CANCEL);

        cancelAllButton.setIcon(
                IconProvider.ICON_CANCEL_ALL);

        clearButton.setIcon(
                IconProvider.ICON_DELETE);
    }

    /*
     * Represents one logical transfer group result.
     *
     * This is intentionally a UI-only object.
     * The actual state remains owned by TransferGroup.
     */
    private static final class GroupResult {

        private final TransferGroup group;

        private final String repository;
        private final String bucket;
        private final String prefix;

        private final boolean sourceRefreshRequired;

        private final Instant completedAt;

        private GroupResult(
                TransferGroup group,
                TransferGroupCompletedEvent event) {

            this.group = group;

            this.repository =
                    event.getRepository();

            this.bucket =
                    event.getBucket();

            this.prefix =
                    event.getPrefix();

            this.sourceRefreshRequired =
                    event.isSourceRefreshRequired();

            this.completedAt =
                    Instant.now();
        }

        private String getDisplayName() {

            String displayName =
                    group.getDisplayName();

            if (displayName == null
                    || displayName.isBlank()) {

                return "Transfer";
            }

            return displayName;
        }

        private boolean isSuccessful() {

            return group.isFullySuccessful();
        }

        private boolean hasSkipped() {

            return group.getSkipped() > 0;
        }

        private boolean hasFailed() {

            return group.getFailed() > 0
                    || group.getCancelled() > 0;
        }

        private String getStatusText() {

            if (isSuccessful()) {
                return "Completed";
            }

            if (hasFailed()) {
                return "Failed";
            }

            if (hasSkipped()) {
                return "Completed with skipped items";
            }

            return "Completed";
        }

        private String getStatusSymbol() {

            if (isSuccessful()) {
                return "✓";
            }

            if (hasFailed()) {
                return "✕";
            }

            if (hasSkipped()) {
                return "⚠";
            }

            return "•";
        }

        private String getSummary() {

            return group.getCompleted()
                    + " copied • "
                    + group.getFailed()
                    + " failed • "
                    + group.getSkipped()
                    + " skipped";
        }

        private String createDetails() {

            StringBuilder builder =
                    new StringBuilder();

            builder.append(
                    getDisplayName());

            builder.append(
                    "\nStatus: ");

            builder.append(
                    getStatusText());

            builder.append(
                    "\nSuccessful: ");

            builder.append(
                    group.getCompleted());

            builder.append(
                    "\nFailed: ");

            builder.append(
                    group.getFailed());

            builder.append(
                    "\nCancelled: ");

            builder.append(
                    group.getCancelled());

            builder.append(
                    "\nSkipped: ");

            builder.append(
                    group.getSkipped());

            builder.append(
                    "\nTotal: ");

            builder.append(
                    group.getTotal());

            builder.append(
                    "\nCompleted: ");

            builder.append(
                    GROUP_RESULT_TIME_FORMAT.format(
                            completedAt));

            if (repository != null
                    && !repository.isBlank()) {

                builder.append(
                        "\nRepository: ");

                builder.append(
                        repository);
            }

            if (bucket != null
                    && !bucket.isBlank()) {

                builder.append(
                        "\nBucket: ");

                builder.append(
                        bucket);
            }

            if (prefix != null
                    && !prefix.isBlank()) {

                builder.append(
                        "\nPrefix: ");

                builder.append(
                        prefix);
            }

            if (sourceRefreshRequired) {

                builder.append(
                        "\nSource refresh: required");
            }

            List<TransferTask> failedTasks =
                    group.getFailedTasks();

            if (!failedTasks.isEmpty()) {

                builder.append(
                        "\n\nFailed tasks:");

                for (TransferTask task :
                        failedTasks) {

                    if (task == null) {
                        continue;
                    }

                    builder.append(
                            "\n- ");

                    builder.append(
                            task.toString());
                }
            }

            return builder.toString();
        }

        @Override
        public String toString() {

            return getStatusSymbol()
                    + " "
                    + getDisplayName()
                    + "    "
                    + getStatusText()
                    + "    "
                    + getSummary();
        }
    }

    private static final class GroupResultRenderer
            extends JPanel
            implements ListCellRenderer<GroupResult> {

        private final JLabel statusLabel =
                new JLabel();

        private final JLabel operationLabel =
                new JLabel();

        private final JLabel summaryLabel =
                new JLabel();

        private GroupResultRenderer() {

            setLayout(
                    new BorderLayout(
                            8,
                            0));

            setBorder(
                    new EmptyBorder(
                            4,
                            8,
                            4,
                            8));

            JPanel center =
                    new JPanel(
                            new BorderLayout());

            center.add(
                    operationLabel,
                    BorderLayout.NORTH);

            center.add(
                    summaryLabel,
                    BorderLayout.SOUTH);

            add(
                    statusLabel,
                    BorderLayout.WEST);

            add(
                    center,
                    BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends GroupResult> list,
                GroupResult value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            if (value == null) {
                return this;
            }

            statusLabel.setText(
                    value.getStatusSymbol());

            operationLabel.setText(
                    value.getDisplayName()
                            + "  —  "
                            + value.getStatusText());

            summaryLabel.setText(
                    value.getSummary());

            if (isSelected) {

                setBackground(
                        list.getSelectionBackground());

                operationLabel.setForeground(
                        list.getSelectionForeground());

                summaryLabel.setForeground(
                        list.getSelectionForeground());

                statusLabel.setForeground(
                        list.getSelectionForeground());

            } else {

                setBackground(
                        list.getBackground());

                operationLabel.setForeground(
                        list.getForeground());

                summaryLabel.setForeground(
                        list.getForeground());

                statusLabel.setForeground(
                        list.getForeground());
            }

            setOpaque(true);

            return this;
        }
    }

    private JTable createGroupTable(
            TransferGroupTableModel model) {

        JTable table =
                new JTable(model);

        table.setRowHeight(54);

        table.setAutoCreateRowSorter(false);
        table.setRowSorter(null);

        table.getTableHeader()
                .setReorderingAllowed(false);

        table.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(90);

        table.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(180);

        table.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(500);

        table.getColumnModel()
                .getColumn(3)
                .setPreferredWidth(110);

        table.getColumnModel()
                .getColumn(4)
                .setPreferredWidth(220);

        return table;
    }

    private void refreshGroupTables() {

        runningGroupModel.setRows(
                groupStateStore.runningSnapshot());

        finishedGroupModel.setRows(
                groupStateStore.finishedSnapshot());
    }
}