
package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryBackupService;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryEnvironment;
import com.company.s3explorer.repository.RepositoryManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RepositoryTransferDialog {

    private static final RepositoryBackupService BACKUP_SERVICE =
            new RepositoryBackupService();
    private static File lastUsedDirectory;

    private RepositoryTransferDialog() {
    }

    public static void exportSelected(
            Component parent,
            List<RepositoryDefinition> repositories) {

        if (repositories == null || repositories.isEmpty()) {
            showError(parent, "Select at least one repository to export.");
            return;
        }

        String suggestedName = repositories.size() == 1
                ? "s3-explorer-export-repository-" + safeFileName(
                repositories.get(0).getName()) + ".zip"
                : "s3-explorer-export-repositories-selected.zip";

        exportRepositories(parent, repositories, suggestedName);
    }

    public static void exportAll(
            Component parent,
            RepositoryManager repositoryManager) {

        List<RepositoryDefinition> repositories =
                repositoryManager.getRepositories();

        if (repositories.isEmpty()) {
            showError(parent, "There are no repositories to export.");
            return;
        }

        exportRepositories(
                parent,
                repositories,
                "s3-explorer-export-repositories-all.zip");
    }

    private static void exportRepositories(
            Component parent,
            List<RepositoryDefinition> repositories,
            String suggestedFileName) {

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Repositories");
        prepareChooser(chooser);
        chooser.setSelectedFile(new File(suggestedFileName));
        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "Encrypted ZIP backup (*.zip)",
                        "zip"));

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        rememberDirectory(selectedFile);
        String fileName = selectedFile.getName();

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            selectedFile = new File(
                    selectedFile.getParentFile(),
                    fileName + ".zip");
        }

        if (selectedFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(
                    parent,
                    "The file already exists. Overwrite it?",
                    "Confirm Export",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        char[] password = null;

        try {
            password = requestNewPassword(parent);

            if (password == null) {
                return;
            }

            BACKUP_SERVICE.exportToZip(
                    new ArrayList<>(repositories),
                    selectedFile.toPath(),
                    password);

            JOptionPane.showMessageDialog(
                    parent,
                    "Repository backup created successfully:\n"
                            + selectedFile.getAbsolutePath(),
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            showError(
                    parent,
                    "Could not export repositories:\n"
                            + safeMessage(ex));
        } finally {
            clear(password);
        }
    }

    public static void importBackup(
            Component parent,
            RepositoryManager repositoryManager) {

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Repository Backup");
        prepareChooser(chooser);
        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "Encrypted ZIP backup (*.zip)",
                        "zip"));

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        rememberDirectory(chooser.getSelectedFile());

        char[] password = requestPassword(parent);

        if (password == null) {
            return;
        }

        try {
            List<RepositoryDefinition> imported =
                    BACKUP_SERVICE.importFromZip(
                            chooser.getSelectedFile().toPath(),
                            password);

            PreviewResult preview = showPreview(parent, imported);

            if (!preview.confirmed() || preview.repositories().isEmpty()) {
                return;
            }

            applyImport(
                    parent,
                    repositoryManager,
                    preview.repositories());

        } catch (Exception ex) {
            showError(
                    parent,
                    "Could not import the backup.\n"
                            + "Check the password and ZIP file.\n\n"
                            + safeMessage(ex));
        } finally {
            clear(password);
        }
    }

    private static char[] requestNewPassword(Component parent) {
        JPasswordField firstField = new JPasswordField(22);
        JPasswordField secondField = new JPasswordField(22);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel("Backup password:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(firstField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Confirm password:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(secondField, gbc);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    parent,
                    panel,
                    "Protect Backup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                clear(firstField.getPassword());
                clear(secondField.getPassword());
                return null;
            }

            char[] first = firstField.getPassword();
            char[] second = secondField.getPassword();

            if (first.length < 8) {
                clear(first);
                clear(second);

                JOptionPane.showMessageDialog(
                        parent,
                        "Use a password containing at least 8 characters.",
                        "Password Too Short",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            if (!Arrays.equals(first, second)) {
                clear(first);
                clear(second);
                firstField.setText("");
                secondField.setText("");

                JOptionPane.showMessageDialog(
                        parent,
                        "The passwords do not match.",
                        "Password Mismatch",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            clear(second);
            return first;
        }
    }

    private static char[] requestPassword(Component parent) {
        JPasswordField passwordField = new JPasswordField(22);

        int result = JOptionPane.showConfirmDialog(
                parent,
                passwordField,
                "Enter Backup Password",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            clear(passwordField.getPassword());
            return null;
        }

        char[] password = passwordField.getPassword();

        if (password.length < 8) {
            clear(password);
            showError(
                    parent,
                    "The password must contain at least 8 characters.");
            return null;
        }

        return password;
    }

    /**
     * Displays a preview and lets the user import selected rows or all rows.
     */

    private static PreviewResult showPreview(
            Component parent,
            List<RepositoryDefinition> repositories) {

        Window owner = SwingUtilities.getWindowAncestor(parent);

        JDialog dialog = new JDialog(
                owner,
                "Repository Import Preview",
                Dialog.ModalityType.APPLICATION_MODAL);

        String[] columns = {
                "Name",
                "Environment",
                "Endpoint",
                "Access Key"
        };

        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (RepositoryDefinition repository : repositories) {
            model.addRow(new Object[]{
                    repository.getName(),
                    repository.getEnvironment(),
                    repository.getEndpoint(),
                    repository.getAccessKey()
            });
        }

        JTable table = new JTable(model);
        table.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(table.getRowHeight() + 4);
        table.setFillsViewportHeight(true);

        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(125);
        table.getColumnModel().getColumn(2).setPreferredWidth(300);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(820, 300));

        JLabel description = new JLabel(
                repositories.size()
                        + " repository/repositories found. "
                        + "Select rows or import all.");

        JButton cancelButton = new JButton("Cancel");
        JButton importSelectedButton = new JButton("Import Selected");
        JButton importAllButton = new JButton("Import All");

        // Cancel solda, içe aktarma işlemleri sağda.
        JPanel buttonPanel = new JPanel(new BorderLayout(8, 0));
        buttonPanel.add(cancelButton, BorderLayout.WEST);

        JPanel importButtons = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0));
        importButtons.add(importSelectedButton);
        importButtons.add(importAllButton);

        buttonPanel.add(importButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(description, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        PreviewResult[] result = {
                new PreviewResult(false, List.of())
        };

        importSelectedButton.addActionListener(e -> {
            int[] selectedRows = table.getSelectedRows();

            if (selectedRows.length == 0) {
                JOptionPane.showMessageDialog(
                        dialog,
                        "Select at least one repository.",
                        "Nothing Selected",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            List<RepositoryDefinition> selectedRepositories =
                    new ArrayList<>();

            for (int viewRow : selectedRows) {
                int modelRow = table.convertRowIndexToModel(viewRow);
                selectedRepositories.add(repositories.get(modelRow));
            }

            result[0] = new PreviewResult(
                    true,
                    selectedRepositories);

            dialog.dispose();
        });

        importAllButton.addActionListener(e -> {
            result[0] = new PreviewResult(
                    true,
                    new ArrayList<>(repositories));

            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(700, 350));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    private static void applyImport(
            Component parent,
            RepositoryManager repositoryManager,
            List<RepositoryDefinition> imported) {

        List<RepositoryDefinition> existing =
                repositoryManager.getRepositories();

        List<ImportAction> actions = new ArrayList<>();
        Set<String> reservedIds = new HashSet<>();

        for (RepositoryDefinition repository : existing) {
            reservedIds.add(repository.getId());
        }

        /*
         * Resolve every conflict before applying changes.
         * Closing/cancelling a conflict dialog aborts the import.
         */
        for (RepositoryDefinition incoming : imported) {
            RepositoryDefinition conflict =
                    findConflict(existing, incoming);

            if (conflict == null) {
                actions.add(new ImportAction(
                        ActionType.ADD,
                        null,
                        incoming));

                reservedIds.add(incoming.getId());
                existing.add(incoming);
                continue;
            }

            Object[] options = {
                    "Skip",
                    "Update",
                    "Import as new",
                    "Cancel import"
            };

            int choice = JOptionPane.showOptionDialog(
                    parent,
                    "A repository with this name and environment already exists:\n"
                            + incoming.getId()
                            + "\n\nChoose how to handle the conflict.",
                    "Repository Conflict",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 3 || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }

            if (choice == 0) {
                actions.add(new ImportAction(
                        ActionType.SKIP,
                        conflict,
                        incoming));

            } else if (choice == 1) {
                actions.add(new ImportAction(
                        ActionType.UPDATE,
                        conflict,
                        incoming));

            } else if (choice == 2) {
                RepositoryDefinition renamed =
                        copyWithUniqueName(incoming, reservedIds);

                reservedIds.add(renamed.getId());
                existing.add(renamed);

                actions.add(new ImportAction(
                        ActionType.ADD,
                        null,
                        renamed));
            }
        }

        int added = 0;
        int updated = 0;
        int skipped = 0;

        try {
            for (ImportAction action : actions) {
                switch (action.type()) {
                    case ADD -> {
                        repositoryManager.addRepository(action.incoming());
                        added++;
                    }
                    case UPDATE -> {
                        repositoryManager.updateRepository(
                                action.existing(),
                                action.incoming());
                        updated++;
                    }
                    case SKIP -> skipped++;
                }
            }
        } catch (RuntimeException ex) {
            showError(
                    parent,
                    "An error occurred while applying the import:\n"
                            + safeMessage(ex)
                            + "\nSome earlier changes may already have been saved.");
            return;
        }

        JOptionPane.showMessageDialog(
                parent,
                "Import completed.\n\n"
                        + "Added: " + added + "\n"
                        + "Updated: " + updated + "\n"
                        + "Skipped: " + skipped,
                "Import Complete",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static RepositoryDefinition findConflict(
            List<RepositoryDefinition> repositories,
            RepositoryDefinition incoming) {

        for (RepositoryDefinition repository : repositories) {
            if (repository.equals(incoming)) {
                return repository;
            }
        }

        return null;
    }

    private static RepositoryDefinition copyWithUniqueName(
            RepositoryDefinition source,
            Set<String> reservedIds) {

        String baseName = source.getName() + " (Imported)";
        String candidateName = baseName;
        int suffix = 2;

        RepositoryDefinition candidate =
                copyWithName(source, candidateName);

        while (reservedIds.contains(candidate.getId())) {
            candidateName = baseName + " " + suffix++;
            candidate = copyWithName(source, candidateName);
        }

        return candidate;
    }

    private static RepositoryDefinition copyWithName(
            RepositoryDefinition source,
            String name) {

        RepositoryDefinition copy = new RepositoryDefinition(
                name,
                source.getEnvironment());

        copy.setEndpoint(source.getEndpoint());
        copy.setAccessKey(source.getAccessKey());
        copy.setSecretKey(source.getSecretKey());
        copy.setExternalBuckets(source.getExternalBuckets());
        copy.setEncryptionTransformation(
                source.getEncryptionTransformation());
        copy.setEncryptionIv(source.getEncryptionIv());
        copy.setEncryptionKey(source.getEncryptionKey());

        return copy;
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "repository";
        }

        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();

        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private static void showError(
            Component parent,
            String message) {

        JOptionPane.showMessageDialog(
                parent,
                message,
                "Repository Backup",
                JOptionPane.ERROR_MESSAGE);
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private enum ActionType {
        ADD,
        UPDATE,
        SKIP
    }

    private record ImportAction(
            ActionType type,
            RepositoryDefinition existing,
            RepositoryDefinition incoming) {
    }

    private record PreviewResult(
            boolean confirmed,
            List<RepositoryDefinition> repositories) {
    }


    private static void prepareChooser(JFileChooser chooser) {
        if (lastUsedDirectory != null
                && lastUsedDirectory.isDirectory()) {
            chooser.setCurrentDirectory(lastUsedDirectory);
        }
    }

    private static void rememberDirectory(File selectedFile) {
        if (selectedFile == null) {
            return;
        }

        File directory = selectedFile.isDirectory()
                ? selectedFile
                : selectedFile.getAbsoluteFile().getParentFile();

        if (directory != null && directory.isDirectory()) {
            lastUsedDirectory = directory;
        }
    }

}
