package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.producer.ProducerRuntime;
import com.company.s3explorer.transfer.renderer.*;
import com.company.s3explorer.transfer.state.TransferStateStore;
import com.company.s3explorer.ui.icons.IconProvider;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
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

    private ProducerTableModel producerTableModel;

    private JTable producerTable;

    private JTable queuedTable;
    private JTable runningTable;
    private JTable finishedTable;
    private JTable allTable;

    private JTabbedPane tabs;

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

        allTable =
                createTable(
                        allModel);

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

    private void layoutComponents() {

        JPanel toolbar =
                new JPanel(
                        new FlowLayout());

        toolbar.setPreferredSize(
                new Dimension(50, 0));

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

        JScrollPane producerScrollPane =
                new JScrollPane(producerTable);

        producerScrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        producerScrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        producerScrollPane.setPreferredSize(
                new Dimension(0, 48));

        producerScrollPane.setMinimumSize(
                new Dimension(0, 48));

        producerScrollPane.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 48));

        contentPanel.add(
                producerScrollPane,
                BorderLayout.NORTH);

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
    public void onProducerUpdated(
            ProducerRuntime runtime) {

        pendingProducerUpdate = runtime;
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

        long running =
                stateStore.getRunningCount();

        long finished =
                stateStore.getFinishedCount();

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

        cancelAllButton.setEnabled(
                queued > 0
                        || running > 0);

        clearButton.setEnabled(
                finished > 0);
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

        if (stateStore.getFinishedCount() <= 0) {
            return;
        }

        clearButton.setEnabled(false);

        CompletableFuture
                .runAsync(
                        stateStore::removeFinished)
                .whenComplete(
                        (ignored, error) -> {

                            SwingUtilities.invokeLater(() -> {

                                clearButton.setEnabled(
                                        true);

                                if (error != null) {

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
                                 * Timer zaten state version değişikliğini
                                 * algılayacak.
                                 *
                                 * Burada sadece hemen görsel güncelleme
                                 * yapıyoruz.
                                 */
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
}