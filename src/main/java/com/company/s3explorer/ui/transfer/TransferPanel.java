package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;
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
    private JTabbedPane tabs;
    private JTable allTable;
    private JTable runningTable;
    private JTable queuedTable;
    private JTable finishedTable;
    private final List<JTable> tables = new ArrayList<>();
    private final ConcurrentHashMap<UUID, TransferRuntime> pendingUpdates = new ConcurrentHashMap<>();
    private Timer refreshTimer;

    public TransferPanel(TransferEventBus eventBus, TransferManager transferManager) {
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

        cancelButton.addActionListener(e -> cancelSelectedTransfers());
        cancelAllButton.addActionListener(e -> cancelAllTransfers());
        clearButton.addActionListener(e -> clearFinishedTransfers());

        this.setButtonIcons();

        tabs = new JTabbedPane();
        allTable = createTable(null);
        queuedTable = createTable(task -> task.getStatus() == TransferStatus.QUEUED);
        runningTable = createTable(task -> task.getStatus() == TransferStatus.RUNNING);
        finishedTable = createTable(runtime -> runtime.getStatus().isFinished());

        tables.add(queuedTable);
        tables.add(runningTable);
        tables.add(finishedTable);
        tables.add(allTable);

        refreshTimer = new Timer(100, e -> flushPendingUpdates());
        refreshTimer.start();
    }

    private void layoutComponents() {
        JPanel toolbar = new JPanel(new FlowLayout());
        toolbar.setPreferredSize(new Dimension(50, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(50, 5, 10, 5));
        toolbar.add(cancelButton);
        toolbar.add(cancelAllButton);
        toolbar.add(clearButton);

        tabs.addTab("Queued", new JScrollPane(queuedTable));
        tabs.addTab("Running", new JScrollPane(runningTable));
        tabs.addTab("Finished", new JScrollPane(finishedTable));
        tabs.addTab("All", new JScrollPane(allTable));
        tabs.setSelectedIndex(1);
        
        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);
    }

    private void registerListeners() {
        eventBus.subscribe(this);

        registerSelectionListener(allTable);
        registerSelectionListener(runningTable);
        registerSelectionListener(queuedTable);
        registerSelectionListener(finishedTable);

        tabs.addChangeListener(e -> updateButtons());
    }

    private void registerSelectionListener(JTable table) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
    }

    private void updateButtons() {
        JTable table = getSelectedTable();

        cancelButton.setEnabled(hasCancelableSelection(table));

        boolean hasActive = tableModel.getQueuedCount() > 0 || tableModel.getRunningCount() > 0;
        cancelAllButton.setEnabled(hasActive);

        boolean hasFinished = tableModel.getCompletedCount() + tableModel.getFailedCount() + tableModel.getCancelledCount() > 0;
        clearButton.setEnabled(hasFinished);
    }

    @Override
    public void onTransferUpdated(TransferRuntime runtime) {
        pendingUpdates.put(runtime.getTask().getId(), runtime);
        /*
        SwingUtilities.invokeLater(() -> {
            TransferUpdateType type = tableModel.upsertTask(event.getRuntime());
            if (type == TransferUpdateType.STATUS_CHANGED) {
                refreshSorters();
            }

            updateTabTitles();
            updateButtons();
        });*/
    }

    private void refreshSorters() {
        for (JTable table : tables) {
            refreshSorter(table);
        }
    }

    private void refreshSorter(JTable table) {
        RowSorter<? extends TableModel> sorter = table.getRowSorter();
        if (sorter instanceof DefaultRowSorter<?, ?> defaultSorter) {
            defaultSorter.sort();
        }
    }

    private void updateTabTitles() {
        long running = tableModel.getRunningCount();
        long queued = tableModel.getQueuedCount();
        long finished = tableModel.getCompletedCount() + tableModel.getFailedCount() + tableModel.getCancelledCount();

        tabs.setTitleAt(0, "Queued (" + queued + ")");
        tabs.setTitleAt(1, "Running (" + running + ")");
        tabs.setTitleAt(2, "Finished (" + finished + ")");
        tabs.setTitleAt(3, "All (" + tableModel.getRowCount() + ")");

        boolean hasActive = running > 0 || queued > 0;
        cancelAllButton.setEnabled(hasActive);
        cancelButton.setEnabled(hasActive);

        boolean hasFinished = finished > 0;
        clearButton.setEnabled(hasFinished);
    }

    private JTable createTable(Predicate<TransferRuntime> predicate) {
        JTable table = new JTable(tableModel);
        TableRowSorter<TransferTableModel> sorter = new TableRowSorter<>(tableModel);
        if (predicate != null) {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends TransferTableModel, ? extends Integer> entry) {
                    TransferRuntime runtime = tableModel.getRuntime(entry.getIdentifier());
                    return predicate.test(runtime);
                }
            });
        }

        // Filtreleme için sorter olacak, ama sort etmesini istemiyoruz
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            sorter.setSortable(i, false);
        }

        table.setRowSorter(sorter);
        table.setRowHeight(54);

        table.getColumnModel().getColumn(0).setPreferredWidth(1);
        table.getColumnModel().getColumn(1).setPreferredWidth(500);
        table.getColumnModel().getColumn(2).setPreferredWidth(1);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(1);
        table.getColumnModel().getColumn(5).setPreferredWidth(1);
        table.getColumnModel().getColumn(6).setPreferredWidth(1);
        table.getColumnModel().getColumn(7).setPreferredWidth(1);
        table.getColumnModel().getColumn(8).setPreferredWidth(1);

        table.getColumnModel().getColumn(0).setCellRenderer(new TypeRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new FileSizeRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new ProgressBarRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new InstantRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new InstantRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new LongFormatRenderer());

        // Sütunların sürüklenerek yer değiştirmesini engeller
        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    private JTable getSelectedTable() {
        Component component = tabs.getSelectedComponent();
        if (!(component instanceof JScrollPane scrollPane)) {
            return null;
        }

        JViewport viewport = scrollPane.getViewport();
        if (viewport.getView() instanceof JTable table) {
            return table;
        }

        return null;
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

        for (int viewRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            TransferRuntime runtime = tableModel.getRuntimeAtModelRow(modelRow);
            if (runtime == null) {
                continue;
            }

            TransferStatus status = runtime.getStatus();
            if (status.isActive()) {
                transferManager.cancel( runtime.getTask().getId());
            }
        }
    }

    private void cancelAllTransfers() {
        int result = JOptionPane.showConfirmDialog(
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
        cancelButton.setIcon(IconProvider.ICON_CANCEL);
        cancelAllButton.setIcon(IconProvider.ICON_CANCEL_ALL);
        clearButton.setIcon(IconProvider.ICON_DELETE);
    }

    private boolean hasCancelableSelection(JTable table) {
        if (table == null) {
            return false;
        }

        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            TransferRuntime runtime = tableModel.getRuntimeAtModelRow(modelRow);
            if (runtime == null) {
                continue;
            }

            TransferStatus status = runtime.getStatus();
            if (status.isActive()) {
                return true;
            }
        }

        return false;
    }

    private void flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            List<TransferRuntime> updates = new ArrayList<>(pendingUpdates.values());
            pendingUpdates.clear();
            boolean structureChanged = false;
            for (TransferRuntime runtime : updates) {
                structureChanged |= tableModel.upsertTask(runtime) != TransferUpdateType.UPDATED;
            }

            if (structureChanged) {
                tableModel.fireStructureRefresh();
                refreshSorters();
            }

            updateTabTitles();
        });
    }
}