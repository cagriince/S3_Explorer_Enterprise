package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.producer.ProducerRuntime;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class TransferPanel extends JPanel implements TransferListener {

    private final TransferEventBus eventBus;
    private final TransferManager transferManager;

    private JButton cancelButton;
    private JButton cancelAllButton;
    private JButton clearButton;

    private TransferTableModel tableModel;
    private ProducerTableModel producerTableModel;

    private JTable producerTable;

    private JTabbedPane tabs;

    private JTable allTable;
    private JTable runningTable;
    private JTable queuedTable;
    private JTable finishedTable;

    private final List<JTable> tables = new ArrayList<>();

    private final ConcurrentHashMap<UUID, TransferRuntime> pendingUpdates =
            new ConcurrentHashMap<>();

    private volatile ProducerRuntime pendingProducerUpdate;

    private Timer refreshTimer;

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
        updateTabTitles();
    }

    private void createModel() {
        tableModel = new TransferTableModel();
        producerTableModel = new ProducerTableModel();
    }

    private void createComponents() {

        cancelButton = new JButton();
        cancelButton.setToolTipText("Cancel Selected");
        cancelButton.setPreferredSize(new Dimension(30, 30));

        cancelAllButton = new JButton();
        cancelAllButton.setToolTipText("Cancel All");
        cancelAllButton.setPreferredSize(new Dimension(30, 30));

        clearButton = new JButton();
        clearButton.setToolTipText("Clear Logs");
        clearButton.setPreferredSize(new Dimension(30, 30));

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

        producerTable = createProducerTable();

        tabs = new JTabbedPane();

        allTable = createTable(null);

        queuedTable = createTable(
                task -> task.getStatus() == TransferStatus.QUEUED);

        runningTable = createTable(
                task -> task.getStatus() == TransferStatus.RUNNING);

        finishedTable = createTable(
                runtime -> runtime.getStatus().isFinished());

        tables.add(queuedTable);
        tables.add(runningTable);
        tables.add(finishedTable);
        tables.add(allTable);

        refreshTimer = new Timer(
                100,
                e -> flushPendingUpdates());

        refreshTimer.start();
    }

    private void layoutComponents() {

        JPanel toolbar = new JPanel(new FlowLayout());
        toolbar.setPreferredSize(new Dimension(50, 0));
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
                new JScrollPane(queuedTable));

        tabs.addTab(
                "Running",
                new JScrollPane(runningTable));

        tabs.addTab(
                "Finished",
                new JScrollPane(finishedTable));

        tabs.addTab(
                "All",
                new JScrollPane(allTable));

        tabs.setSelectedIndex(1);

        JPanel contentPanel = new JPanel(new BorderLayout());

        contentPanel.add(
                new JScrollPane(producerTable),
                BorderLayout.NORTH);

        contentPanel.add(
                tabs,
                BorderLayout.CENTER);

        setLayout(new BorderLayout());

        add(toolbar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
    }

    private void registerListeners() {

        eventBus.subscribe(this);

        registerSelectionListener(allTable);
        registerSelectionListener(runningTable);
        registerSelectionListener(queuedTable);
        registerSelectionListener(finishedTable);

        tabs.addChangeListener(
                e -> updateButtons());
    }

    private void registerSelectionListener(JTable table) {

        table.getSelectionModel()
                .addListSelectionListener(e -> {

                    if (!e.getValueIsAdjusting()) {
                        updateButtons();
                    }
                });
    }

    @Override
    public void onTransferUpdated(TransferRuntime runtime) {

        pendingUpdates.put(
                runtime.getTask().getId(),
                runtime);
    }

    @Override
    public void onProducerUpdated(ProducerRuntime runtime) {

        pendingProducerUpdate = runtime;
    }

    private void flushPendingUpdates() {

        if (pendingUpdates.isEmpty()
                && pendingProducerUpdate == null) {

            return;
        }

        SwingUtilities.invokeLater(() -> {

            List<TransferRuntime> updates =
                    new ArrayList<>(pendingUpdates.values());

            pendingUpdates.clear();

            for (TransferRuntime runtime : updates) {

                TransferUpdateType type =
                        tableModel.upsertTask(runtime);

                if (type == TransferUpdateType.STATUS_CHANGED) {
                    refreshSorters();
                }
            }

            ProducerRuntime producer =
                    pendingProducerUpdate;

            pendingProducerUpdate = null;

            if (producer != null) {
                producerTableModel.update(producer);
            }

            if (!updates.isEmpty()) {
                updateTabTitles();
                updateButtons();
            }

            if (producer != null) {
                updateProducerVisibility(producer);
            }
        });
    }

    private void updateProducerVisibility(
            ProducerRuntime runtime) {

        if (runtime == null) {
            producerTable.setVisible(false);
            return;
        }

        producerTable.setVisible(true);

        if (runtime.getStatus().isFinished()) {

            Timer timer = new Timer(
                    3000,
                    e -> {

                        producerTableModel.clear();
                        producerTable.setVisible(false);
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
                new JTable(producerTableModel);

        table.setRowHeight(40);
        table.setFocusable(false);
        table.setEnabled(false);

        table.getTableHeader()
                .setReorderingAllowed(false);

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

    private void refreshSorters() {

        for (JTable table : tables) {
            refreshSorter(table);
        }
    }

    private void refreshSorter(JTable table) {

        RowSorter<? extends TableModel> sorter =
                table.getRowSorter();

        if (sorter instanceof DefaultRowSorter<?, ?> defaultSorter) {
            defaultSorter.sort();
        }
    }

    private void updateTabTitles() {

        long running =
                tableModel.getRunningCount();

        long queued =
                tableModel.getQueuedCount();

        long finished =
                tableModel.getCompletedCount()
                        + tableModel.getFailedCount()
                        + tableModel.getCancelledCount();

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
                "All (" + tableModel.getRowCount() + ")");

        boolean hasActive =
                running > 0 || queued > 0;

        cancelAllButton.setEnabled(hasActive);

        boolean hasFinished =
                finished > 0;

        clearButton.setEnabled(hasFinished);

        updateButtons();
    }

    private void updateButtons() {

        JTable table =
                getSelectedTable();

        cancelButton.setEnabled(
                hasCancelableSelection(table));

        boolean hasActive =
                tableModel.getQueuedCount() > 0
                        || tableModel.getRunningCount() > 0;

        cancelAllButton.setEnabled(hasActive);
    }

    private JTable createTable(
            Predicate<TransferRuntime> predicate) {

        JTable table =
                new JTable(tableModel);

        TableRowSorter<TransferTableModel> sorter =
                new TableRowSorter<>(tableModel);

        if (predicate != null) {

            sorter.setRowFilter(
                    new RowFilter<>() {

                        @Override
                        public boolean include(
                                Entry<? extends TransferTableModel,
                                        ? extends Integer> entry) {

                            TransferRuntime runtime =
                                    tableModel.getRuntime(
                                            entry.getIdentifier());

                            return runtime != null
                                    && predicate.test(runtime);
                        }
                    });
        }

        for (int i = 0;
             i < tableModel.getColumnCount();
             i++) {

            sorter.setSortable(i, false);
        }

        table.setRowSorter(sorter);
        table.setRowHeight(54);

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
                .setCellRenderer(new TypeRenderer());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(new FileSizeRenderer());

        table.getColumnModel()
                .getColumn(3)
                .setCellRenderer(new ProgressBarRenderer());

        table.getColumnModel()
                .getColumn(4)
                .setCellRenderer(new StatusRenderer());

        table.getColumnModel()
                .getColumn(5)
                .setCellRenderer(new InstantRenderer());

        table.getColumnModel()
                .getColumn(6)
                .setCellRenderer(new InstantRenderer());

        table.getColumnModel()
                .getColumn(7)
                .setCellRenderer(new LongFormatRenderer());

        table.getTableHeader()
                .setReorderingAllowed(false);

        return table;
    }

    private JTable getSelectedTable() {

        Component component =
                tabs.getSelectedComponent();

        if (!(component instanceof JScrollPane scrollPane)) {
            return null;
        }

        JViewport viewport =
                scrollPane.getViewport();

        if (viewport.getView() instanceof JTable table) {
            return table;
        }

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

        for (int viewRow : selectedRows) {

            int modelRow =
                    table.convertRowIndexToModel(viewRow);

            TransferRuntime runtime =
                    tableModel.getRuntimeAtModelRow(
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

        transferManager.cancelAll();
    }

    private void clearFinishedTransfers() {

        tableModel.removeFinished();

        updateTabTitles();
    }

    public void setButtonIcons() {

        cancelButton.setIcon(
                IconProvider.ICON_CANCEL);

        cancelAllButton.setIcon(
                IconProvider.ICON_CANCEL_ALL);

        clearButton.setIcon(
                IconProvider.ICON_DELETE);
    }

    private boolean hasCancelableSelection(
            JTable table) {

        if (table == null) {
            return false;
        }

        for (int viewRow :
                table.getSelectedRows()) {

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            TransferRuntime runtime =
                    tableModel.getRuntimeAtModelRow(
                            modelRow);

            if (runtime != null
                    && runtime.getStatus().isActive()) {

                return true;
            }
        }

        return false;
    }
}