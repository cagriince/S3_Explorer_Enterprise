package com.company.s3explorer.ui.repository;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.ConnectionTestResult;
import com.company.s3explorer.service.S3ClientFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RepositoryPanel extends JPanel {

    private final RepositoryManager repositoryManager;
    private final S3ClientFactory clientFactory;

    private JTable table;
    private RepositoryTableModel tableModel;

    private JButton editBtn;
    private JButton duplicateBtn;
    private JButton deleteBtn;
    private JButton testBtn;

    private boolean testRunning;
    
    public RepositoryPanel(
            RepositoryManager repositoryManager,
            S3ClientFactory clientFactory) {

        this.repositoryManager =
                repositoryManager;

        this.clientFactory =
                clientFactory;

        initialize();
    }

    private void initialize() {

        setLayout(
                new BorderLayout());

        setBorder(
                BorderFactory.createEmptyBorder(
                        5,
                        5,
                        5,
                        5));

        add(
                createToolbar(),
                BorderLayout.NORTH);

        add(
                createTable(),
                BorderLayout.CENTER);

        reloadRepositories();
    }

    private JScrollPane createTable() {

        tableModel =
                new RepositoryTableModel();

        table =
                new JTable(tableModel);

        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);

        table.getSelectionModel()
                .addListSelectionListener(e -> {

                    if (e.getValueIsAdjusting()) {
                        return;
                    }

                    updateRepositoryActionButtons();
                });

        table.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            MouseEvent e) {

                        if (e.getClickCount() == 2) {
                            editRepository();
                        }
                    }
                });

        InputMap inputMap =
                table.getInputMap(
                        JComponent
                                .WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        ActionMap actionMap =
                table.getActionMap();

        inputMap.put(
                KeyStroke.getKeyStroke("ENTER"),
                "edit");

        actionMap.put(
                "edit",
                new AbstractAction() {

                    @Override
                    public void actionPerformed(
                            ActionEvent e) {

                        editRepository();
                    }
                });

        updateRepositoryActionButtons();

        return new JScrollPane(table);
    }

    private JPanel createToolbar() {

        JPanel panel =
                new JPanel();

        JButton addBtn =
                new JButton("Add");

        editBtn =
                new JButton("Edit");

        duplicateBtn =
                new JButton("Duplicate");

        deleteBtn =
                new JButton("Delete");

        testBtn =
                new JButton("Test");

        addBtn.addActionListener(
                e -> addRepository());

        editBtn.addActionListener(
                e -> editRepository());

        duplicateBtn.addActionListener(
                e -> duplicateRepository());

        deleteBtn.addActionListener(
                e -> deleteRepository());

        testBtn.addActionListener(
                e -> testRepository());

        panel.add(addBtn);
        panel.add(editBtn);
        panel.add(duplicateBtn);
        panel.add(deleteBtn);
        panel.add(testBtn);

        return panel;
    }

    private void updateRepositoryActionButtons() {

        boolean repositorySelected =
                getSelectedRepository() != null;

        editBtn.setEnabled(
                repositorySelected);

        duplicateBtn.setEnabled(
                repositorySelected);

        deleteBtn.setEnabled(
                repositorySelected);

        testBtn.setEnabled(
                repositorySelected
                        && !testRunning);
    }
    
    private RepositoryDefinition getSelectedRepository() {

        int row =
                table.getSelectedRow();

        if (row < 0) {
            return null;
        }

        row =
                table.convertRowIndexToModel(row);

        return tableModel.getRepository(row);
    }

    private void addRepository() {

        RepositoryDialog dialog =
                new RepositoryDialog(
                        SwingUtilities
                                .getWindowAncestor(this));

        dialog.setVisible(true);

        RepositoryDefinition repo =
                dialog.getRepository();

        if (repo == null) {
            return;
        }

        repositoryManager.addRepository(
                repo);

        reloadRepositories();
    }

    private void editRepository() {

        RepositoryDefinition selected =
                getSelectedRepository();

        if (selected == null) {
            return;
        }

        RepositoryDialog dialog =
                new RepositoryDialog(
                        SwingUtilities
                                .getWindowAncestor(this),
                        selected);

        dialog.setVisible(true);

        RepositoryDefinition updated =
                dialog.getRepository();

        if (updated == null) {
            return;
        }

        try {

            repositoryManager.updateRepository(
                    selected,
                    updated);

            reloadRepositories();

            selectRepository(
                    updated);

        } catch (IllegalArgumentException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Edit Repository",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void duplicateRepository() {

        RepositoryDefinition selected =
                getSelectedRepository();

        if (selected == null) {
            return;
        }

        String newName =
                JOptionPane.showInputDialog(
                        this,
                        "Enter a new repository name:",
                        "Duplicate Repository",
                        JOptionPane.PLAIN_MESSAGE);

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
                    repositoryManager
                            .duplicateRepository(
                                    selected,
                                    newName);

            reloadRepositories();

            selectRepository(
                    duplicate);

        } catch (IllegalArgumentException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Duplicate Repository",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public void deleteRepository() {

        RepositoryDefinition selected =
                getSelectedRepository();

        if (selected == null) {
            return;
        }

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        "Delete repository "
                                + selected.getName()
                                + "?",
                        "Confirm",
                        JOptionPane.YES_NO_OPTION);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        /*
         * RepositoryManager'daki gerçek metod
         * removeRepository().
         */
        repositoryManager.removeRepository(
                selected);

        reloadRepositories();
    }

    private void testRepository() {

        RepositoryDefinition repo =
                getSelectedRepository();

        if (repo == null) {
            return;
        }

        /*
         * Bağlantı testi başladı.
         * Test tamamlanana kadar Test butonu pasif.
         */
        testButtonState(true);

        SwingWorker<ConnectionTestResult, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected ConnectionTestResult
                    doInBackground() {

                        return clientFactory
                                .testConnection(repo);
                    }

                    @Override
                    protected void done() {

                        try {

                            ConnectionTestResult result =
                                    get();

                            showConnectionResult(
                                    result);

                        } catch (Exception ex) {

                            JOptionPane.showMessageDialog(
                                    RepositoryPanel.this,
                                    ex.getMessage(),
                                    "Test Connection",
                                    JOptionPane.ERROR_MESSAGE);

                        } finally {

                            /*
                             * Test bitti.
                             * Repository hâlâ seçiliyse Test tekrar aktif,
                             * seçim kaldırılmışsa pasif kalır.
                             */
                            testButtonState(false);
                        }
                    }
                };

        worker.execute();
    }

    private void testButtonState(
            boolean running) {

        testRunning = running;

        updateRepositoryActionButtons();
    }

    private void showConnectionResult(
            ConnectionTestResult result) {

        if (result == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Connection test returned no result.",
                    "Test Connection",
                    JOptionPane.ERROR_MESSAGE);

            return;
        }

        int messageType =
                result.isSuccess()
                        ? JOptionPane.INFORMATION_MESSAGE
                        : JOptionPane.ERROR_MESSAGE;

        JOptionPane.showMessageDialog(
                this,
                result.toString(),
                "Test Connection",
                messageType);
    }

    private void reloadRepositories() {

        tableModel.setRepositories(
                repositoryManager
                        .getRepositories());

        updateRepositoryActionButtons();
    }

    private void selectRepository(
            RepositoryDefinition repository) {

        if (repository == null) {
            return;
        }

        for (int row = 0;
             row < tableModel.getRowCount();
             row++) {

            RepositoryDefinition current =
                    tableModel.getRepository(row);

            if (repository.equals(current)) {

                int viewRow =
                        table.convertRowIndexToView(row);

                if (viewRow >= 0) {

                    table.setRowSelectionInterval(
                            viewRow,
                            viewRow);

                    table.scrollRectToVisible(
                            table.getCellRect(
                                    viewRow,
                                    0,
                                    true));
                }

                return;
            }
        }
    }
}