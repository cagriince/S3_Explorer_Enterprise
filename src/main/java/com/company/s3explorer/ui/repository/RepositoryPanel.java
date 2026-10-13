
package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.ConnectionTestResult;
import com.company.s3explorer.service.S3ClientFactory;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class RepositoryPanel extends JPanel {

    private final RepositoryManager repositoryManager;
    private final S3ClientFactory clientFactory;

    private JTable table;
    private RepositoryTableModel tableModel;

    private JButton editBtn;
    private JButton deleteBtn;
    private JButton testBtn;
    private JButton moreBtn;

    private boolean testRunning;

    public RepositoryPanel(
            RepositoryManager repositoryManager,
            S3ClientFactory clientFactory) {

        this.repositoryManager = repositoryManager;
        this.clientFactory = clientFactory;

        initialize();
    }

    private void initialize() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(createToolbar(), BorderLayout.NORTH);
        add(createTable(), BorderLayout.CENTER);

        reloadRepositories();
    }

    private JScrollPane createTable() {
        tableModel = new RepositoryTableModel();
        table = new JTable(tableModel);

        table.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JComponent booleanRenderer =
                (JComponent) table.getDefaultRenderer(Boolean.class);
        booleanRenderer.setOpaque(true);

        JTableHeader header = table.getTableHeader();
        TableCellRenderer defaultHeaderRenderer =
                header.getDefaultRenderer();

        header.setDefaultRenderer(
                (tableHeader, value, isSelected, hasFocus, row, column) -> {
                    Component component =
                            defaultHeaderRenderer.getTableCellRendererComponent(
                                    tableHeader,
                                    value,
                                    isSelected,
                                    hasFocus,
                                    row,
                                    column);

                    if (component instanceof JLabel label) {
                        label.setHorizontalAlignment(SwingConstants.CENTER);
                    }

                    return component;
                });

        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(0).setPreferredWidth(180);
        columns.getColumn(1).setPreferredWidth(120);
        columns.getColumn(2).setPreferredWidth(300);
        columns.getColumn(3).setPreferredWidth(180);
        columns.getColumn(4).setPreferredWidth(110);
        columns.getColumn(5).setPreferredWidth(110);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateRepositoryActionButtons();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2
                        && table.rowAtPoint(e.getPoint()) >= 0) {
                    editRepository();
                }
            }
        });

        updateRepositoryTableRowHeight(table);

        InputMap inputMap = table.getInputMap(
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = table.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "edit");
        actionMap.put("edit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editRepository();
            }
        });

        updateRepositoryActionButtons();

        return new JScrollPane(table);
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 8, 0));

        JButton addBtn = new JButton("Add");
        editBtn = new JButton("Edit");
        testBtn = new JButton("Test");
        moreBtn = new JButton("More >");

        addBtn.addActionListener(e -> addRepository());
        editBtn.addActionListener(e -> editRepository());
        testBtn.addActionListener(e -> testRepository());
        moreBtn.addActionListener(e -> showMoreMenu());

        panel.add(addBtn);
        panel.add(editBtn);
        panel.add(testBtn);
        panel.add(moreBtn);

        return panel;
    }

    private void showMoreMenu() {
        int selectedCount = table.getSelectedRowCount();
        boolean anySelected = selectedCount > 0;
        boolean repositoriesExist =
                !repositoryManager.getRepositories().isEmpty();

        JPopupMenu popup = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setEnabled(anySelected);
        deleteItem.addActionListener(e -> deleteRepository());
        popup.add(deleteItem);

        JMenuItem duplicateItem = new JMenuItem("Duplicate");
        duplicateItem.setEnabled(selectedCount == 1);
        duplicateItem.addActionListener(e -> duplicateRepository());
        popup.add(duplicateItem);

        popup.addSeparator();

        JMenuItem exportSelectedItem =
                new JMenuItem("Export Selected");
        exportSelectedItem.setEnabled(anySelected);
        exportSelectedItem.addActionListener(e ->
                RepositoryTransferDialog.exportSelected(
                        this,
                        getSelectedRepositories()));
        popup.add(exportSelectedItem);

        JMenuItem exportAllItem = new JMenuItem("Export All");
        exportAllItem.setEnabled(repositoriesExist);
        exportAllItem.addActionListener(e ->
                RepositoryTransferDialog.exportAll(
                        this,
                        repositoryManager));
        popup.add(exportAllItem);

        JMenuItem importItem = new JMenuItem("Import");
        importItem.addActionListener(e -> {
            RepositoryTransferDialog.importBackup(
                    this,
                    repositoryManager);
            reloadRepositories();
        });
        popup.add(importItem);

        popup.show(moreBtn, 0, moreBtn.getHeight());
    }

    private void updateRepositoryActionButtons() {
        int selectedCount = table == null
                ? 0
                : table.getSelectedRowCount();

        boolean oneSelected = selectedCount == 1;

        if (editBtn != null) {
            editBtn.setEnabled(oneSelected);
        }

        if (testBtn != null) {
            testBtn.setEnabled(oneSelected && !testRunning);
        }

        if (deleteBtn != null) {
            deleteBtn.setEnabled(selectedCount > 0);
        }
    }

    private List<RepositoryDefinition> getSelectedRepositories() {
        List<RepositoryDefinition> selected = new ArrayList<>();

        if (table == null || tableModel == null) {
            return selected;
        }

        int[] selectedRows = table.getSelectedRows();

        for (int viewRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            selected.add(tableModel.getRepository(modelRow));
        }

        return selected;
    }

    /**
     * Returns a repository only when exactly one row is selected.
     * Edit, Delete, Test and Duplicate require a single selection.
     */
    private RepositoryDefinition getSelectedRepository() {
        List<RepositoryDefinition> selected =
                getSelectedRepositories();

        return selected.size() == 1 ? selected.get(0) : null;
    }

    private void addRepository() {
        RepositoryDialog dialog = new RepositoryDialog(
                SwingUtilities.getWindowAncestor(this));

        dialog.setVisible(true);

        RepositoryDefinition repo = dialog.getRepository();

        if (repo == null) {
            return;
        }

        repositoryManager.addRepository(repo);
        reloadRepositories();
        selectRepository(repo);
    }

    private void editRepository() {
        RepositoryDefinition selected = getSelectedRepository();

        if (selected == null) {
            return;
        }

        RepositoryDialog dialog = new RepositoryDialog(
                SwingUtilities.getWindowAncestor(this),
                selected);

        dialog.setVisible(true);

        RepositoryDefinition updated = dialog.getRepository();

        if (updated == null) {
            return;
        }

        try {
            repositoryManager.updateRepository(selected, updated);
            reloadRepositories();
            selectRepository(updated);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Edit Repository",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void duplicateRepository() {
        RepositoryDefinition selected = getSelectedRepository();

        if (selected == null) {
            return;
        }

        String newName = (String) JOptionPane.showInputDialog(
                this,
                "Enter a new repository name:",
                "Duplicate Repository",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                selected.getName());

        if (newName == null) {
            return;
        }

        newName = newName.trim();

        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Repository name must not be empty.",
                    "Duplicate Repository",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            RepositoryDefinition duplicate =
                    repositoryManager.duplicateRepository(
                            selected,
                            newName);

            reloadRepositories();
            selectRepository(duplicate);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Duplicate Repository",
                    JOptionPane.ERROR_MESSAGE);
        }
    }


    public void deleteRepository() {
        List<RepositoryDefinition> selected =
                getSelectedRepositories();

        if (selected.isEmpty()) {
            return;
        }

        String message;

        if (selected.size() == 1) {
            message = "Delete repository \""
                    + selected.get(0).getName()
                    + "\"?";
        } else {
            message = "Delete the "
                    + selected.size()
                    + " selected repositories?";
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        for (RepositoryDefinition repository : selected) {
            repositoryManager.removeRepository(repository);
        }

        reloadRepositories();
    }

    private void testRepository() {
        RepositoryDefinition repo = getSelectedRepository();

        if (repo == null || testRunning) {
            return;
        }

        testButtonState(true);

        Window owner = SwingUtilities.getWindowAncestor(this);

        JDialog progressDialog = new JDialog(
                owner,
                "Test Connection",
                Dialog.ModalityType.APPLICATION_MODAL);

        JLabel messageLabel = new JLabel(
                "Attempting to establish a repository connection...");

        messageLabel.setBorder(
                BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JButton cancelButton = new JButton("Cancel");

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.add(messageLabel, BorderLayout.NORTH);
        centerPanel.add(progressBar, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(
                new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 12));
        contentPanel.setBorder(
                BorderFactory.createEmptyBorder(16, 18, 12, 18));
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        final boolean[] cancelled = {false};

        SwingWorker<ConnectionTestResult, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected ConnectionTestResult doInBackground() {
                        return clientFactory.testConnection(repo);
                    }

                    @Override
                    protected void done() {
                        if (progressDialog.isDisplayable()) {
                            progressDialog.dispose();
                        }
                    }
                };

        Runnable cancelTest = () -> {
            if (cancelled[0]) {
                return;
            }

            cancelled[0] = true;
            worker.cancel(true);
            progressDialog.dispose();
        };

        cancelButton.addActionListener(e -> cancelTest.run());

        // Pressing ESC performs the same action as clicking Cancel.
        progressDialog.getRootPane().registerKeyboardAction(
                e -> cancelTest.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        progressDialog.setDefaultCloseOperation(
                WindowConstants.DO_NOTHING_ON_CLOSE);

        progressDialog.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(
                            java.awt.event.WindowEvent e) {
                        cancelTest.run();
                    }
                });

        progressDialog.setContentPane(contentPanel);
        progressDialog.pack();
        progressDialog.setResizable(false);
        progressDialog.setLocationRelativeTo(this);

        worker.execute();
        progressDialog.setVisible(true);

        try {
            if (cancelled[0] || worker.isCancelled()) {
                return;
            }

            ConnectionTestResult result = worker.get();
            showConnectionResult(result);

        } catch (java.util.concurrent.CancellationException ex) {
            // The connection test was cancelled.

        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() != null
                    ? ex.getCause()
                    : ex;

            JOptionPane.showMessageDialog(
                    this,
                    cause.getMessage() != null
                            ? cause.getMessage()
                            : "An error occurred while testing the connection.",
                    "Test Connection",
                    JOptionPane.ERROR_MESSAGE);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

        } finally {
            testButtonState(false);
        }
    }

    private void testButtonState(boolean running) {
        testRunning = running;
        updateRepositoryActionButtons();
    }

    private void showConnectionResult(ConnectionTestResult result) {
        if (result == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Connection test returned no result.",
                    "Test Connection",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int messageType = result.isSuccess()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.ERROR_MESSAGE;

        JOptionPane.showMessageDialog(
                this,
                result.toString(),
                "Test Connection",
                messageType);
    }

    private void reloadRepositories() {
        tableModel.setRepositories(repositoryManager.getRepositories());
        updateRepositoryActionButtons();
    }

    private void selectRepository(RepositoryDefinition repository) {
        if (repository == null) {
            return;
        }

        table.clearSelection();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            RepositoryDefinition current = tableModel.getRepository(row);

            if (repository.equals(current)) {
                int viewRow = table.convertRowIndexToView(row);

                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(
                            table.getCellRect(viewRow, 0, true));
                }

                return;
            }
        }
    }

    private void updateRepositoryTableRowHeight(JTable table) {
        if (table == null) {
            return;
        }

        FontMetrics fontMetrics = table.getFontMetrics(table.getFont());
        table.setRowHeight(fontMetrics.getHeight() + 8);
    }
}
