package com.company.s3explorer.ui.repository;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.security.AesCryptoService;
import com.company.s3explorer.service.ConnectionTestResult;
import com.company.s3explorer.service.S3ClientFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RepositoryPanel extends JPanel {

    private final RepositoryManager repositoryManager;
    private final AesCryptoService aesCryptoService;
    private final S3ClientFactory clientFactory;
    private final ActiveRepositoryContext context;

    private JTable table;
    private RepositoryTableModel tableModel;
    private JButton testBtn;
    
    public RepositoryPanel(
            RepositoryManager repositoryManager,
            AesCryptoService aesCryptoService,
            ActiveRepositoryContext context,
            S3ClientFactory clientFactory) {

        this.repositoryManager =
                repositoryManager;

        this.aesCryptoService =
                aesCryptoService;

        this.context =
                context;

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

        return new JScrollPane(table);
    }

    private JPanel createToolbar() {

        JPanel panel =
                new JPanel();

        JButton addBtn =
                new JButton("Add");

        JButton editBtn =
                new JButton("Edit");

        JButton deleteBtn =
                new JButton("Delete");

        testBtn =
                new JButton("Test");

        addBtn.addActionListener(
                e -> addRepository());

        editBtn.addActionListener(
                e -> editRepository());

        deleteBtn.addActionListener(
                e -> deleteRepository());

        testBtn.addActionListener(
                e -> testRepository());

        panel.add(addBtn);
        panel.add(editBtn);
        panel.add(deleteBtn);
        panel.add(testBtn);

        return panel;
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

        repositoryManager.updateRepository(
                selected,
                updated);

        reloadRepositories();
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
         * Bağlantı testi network işlemi yaptığı için
         * Swing Event Dispatch Thread üzerinde
         * çalıştırmıyoruz.
         */
        testButtonState(false);

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

                        testButtonState(true);

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
                        }
                    }
                };

        worker.execute();
    }

    private void testButtonState(
            boolean enabled) {

        testBtn.setEnabled(enabled);
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
    }
}