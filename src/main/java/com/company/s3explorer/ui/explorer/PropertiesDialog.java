package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.FolderProperties;
import com.company.s3explorer.ui.icons.FileIconRegistry;
import com.company.s3explorer.util.DateFormatter;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PropertiesDialog extends JDialog {

    private final JLabel nameValue;
    private final JLabel typeValue;
    private final JLabel locationValue;
    private final JLabel sizeValue;
    private final JLabel lastModifiedValue;
    private final JLabel storageClassValue;
    private final JLabel foldersValue;
    private final JLabel filesValue;
    private final JLabel totalSizeValue;
    private final JLabel statusLabel;
    
    public PropertiesDialog(
            Window owner,
            S3FileItem item) {

        super(
                owner,
                "Properties",
                ModalityType.APPLICATION_MODAL);

        nameValue = new JLabel();
        typeValue = new JLabel();
        locationValue = new JLabel();
        sizeValue = new JLabel();
        lastModifiedValue = new JLabel();
        storageClassValue = new JLabel();
        foldersValue = new JLabel();
        filesValue = new JLabel();
        totalSizeValue = new JLabel();
        statusLabel = new JLabel();

        buildUi();
        setItem(item);

        setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);

        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {

        JPanel contentPanel =
                new JPanel(
                        new BorderLayout(15, 15));

        contentPanel.setBorder(
                new EmptyBorder(
                        10,
                        10,
                        5,
                        10));

        JPanel propertiesPanel =
                new JPanel(
                        new GridBagLayout());

        propertiesPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10,10,10,10)));
        
        GridBagConstraints constraints =
                new GridBagConstraints();

        constraints.insets =
                new Insets(4, 4, 4, 12);

        constraints.anchor =
                GridBagConstraints.WEST;

        constraints.fill =
                GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.gridy = 0;

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Name:",
                nameValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Type:",
                typeValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Location:",
                locationValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Size:",
                sizeValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Last Modified:",
                lastModifiedValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Storage Class:",
                storageClassValue);
        
        addPropertyRow(
                propertiesPanel,
                constraints,
                "Folders:",
                foldersValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Files:",
                filesValue);

        addPropertyRow(
                propertiesPanel,
                constraints,
                "Total Size:",
                totalSizeValue);

        contentPanel.add(
                propertiesPanel,
                BorderLayout.CENTER);

        JPanel bottomPanel =
                new JPanel(
                        new BorderLayout(10, 0));

        statusLabel.setBorder(
                new EmptyBorder(
                        5,
                        4,
                        5,
                        4));

        bottomPanel.add(
                statusLabel,
                BorderLayout.CENTER);

        JButton okButton =
                new JButton("OK");

        okButton.addActionListener(
                e -> dispose());

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                0,
                                0));

        buttonPanel.add(okButton);

        bottomPanel.add(
                buttonPanel,
                BorderLayout.EAST);

        contentPanel.add(
                bottomPanel,
                BorderLayout.SOUTH);

        setContentPane(contentPanel);

        getRootPane().setDefaultButton(
                okButton);

        getRootPane()
                .registerKeyboardAction(
                        e -> dispose(),
                        KeyStroke.getKeyStroke(
                                "ESCAPE"),
                        JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void addPropertyRow(
            JPanel panel,
            GridBagConstraints constraints,
            String label,
            JLabel valueLabel) {

        constraints.gridx = 0;
        constraints.weightx = 0;

        JLabel nameLabel =
                new JLabel(label);

        nameLabel.setFont(
                nameLabel.getFont()
                        .deriveFont(Font.BOLD));

        panel.add(
                nameLabel,
                constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;

        panel.add(
                valueLabel,
                constraints);

        constraints.gridy++;
    }

    private void setItem(
            S3FileItem item) {

        if (item == null) {
            return;
        }

        nameValue.setText(
                item.getName());

        locationValue.setText(
                item.getBucket()
                        + "/"
                        + item.getKey());

        if (item.isFolder()) {

            typeValue.setText(
                    "Folder");

            sizeValue.setText(
                    "-");

            lastModifiedValue.setText("-");
            storageClassValue.setText("-");
            
            foldersValue.setText(
                    "Calculating...");

            filesValue.setText(
                    "Calculating...");

            totalSizeValue.setText(
                    "Calculating...");

            statusLabel.setText(
                    "Calculating folder properties...");

        } else {

            typeValue.setText(
                    FileIconRegistry
                            .findFileType(
                                    item.getKey())
                            .displayName());

            sizeValue.setText(
                    formatSize(
                            item.getSize()));

            lastModifiedValue.setText(
                    DateFormatter.format(
                            item.getLastModified()));

            storageClassValue.setText(
                    item.getStorageClass() == null
                            ? "-"
                            : item.getStorageClass());
            
            foldersValue.setText(
                    "-");

            filesValue.setText(
                    "-");

            totalSizeValue.setText(
                    "-");

            statusLabel.setText(
                    "");

        }
    }

    public void updateFolderProperties(
            FolderProperties properties) {

        if (properties == null) {
            return;
        }

        foldersValue.setText(
                formatCount(
                        properties.folderCount()));

        filesValue.setText(
                formatCount(
                        properties.fileCount()));

        totalSizeValue.setText(
                formatSize(
                        properties.totalSize()));
    }

    public void updateFolderProgress(
            FolderProperties properties) {

        if (properties == null) {
            return;
        }

        foldersValue.setText(
                formatCount(
                        properties.folderCount()));

        filesValue.setText(
                formatCount(
                        properties.fileCount()));

        totalSizeValue.setText(
                formatSize(
                        properties.totalSize()));
    }

    public void setStatus(
            String status) {

        statusLabel.setText(
                status == null
                        ? ""
                        : status);
    }

    public void setCalculationCompleted() {

        statusLabel.setText(
                "Calculation completed.");
    }

    private String formatCount(
            long value) {

        return String.format(
                "%,d",
                value);
    }

    private String formatSize(
            long bytes) {

        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {
                "B",
                "KB",
                "MB",
                "GB",
                "TB",
                "PB"
        };

        int unitIndex = 0;

        while (value >= 1024
                && unitIndex < units.length - 1) {

            value /= 1024;
            unitIndex++;
        }

        return String.format(
                "%.1f %s",
                value,
                units[unitIndex]);
    }
}