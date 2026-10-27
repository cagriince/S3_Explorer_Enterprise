package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.FolderProperties;
import com.company.s3explorer.ui.icons.FileIconRegistry;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.util.DateFormatter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
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
    private Timer statusAnimationTimer;
    private String statusBaseText = "";
    private int statusDotCount = 0;

    public PropertiesDialog(
            Window owner,
            S3FileItem item) {

        this(owner, List.of(item));
    }

    public PropertiesDialog(
            Window owner,
            List<S3FileItem> items) {

        this(owner);

        if (items.size() > 1) {
            setMultipleItems(items);
        }
        else {
            setItem(items.getFirst());
        }

        pack();
        setLocationRelativeTo(owner);
        setSize(
                300,
                350);
    }

    private PropertiesDialog(Window owner) {

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

        setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stopStatusAnimation();
            }
        });

        setResizable(true);
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

        addPropertyRowSeparator(propertiesPanel, constraints);
        
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
        
        addPropertyRowSeparator(propertiesPanel, constraints);
        
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

        addPropertyRow(
                propertiesPanel,
                constraints,
                null,
                statusLabel);

        contentPanel.add(
                propertiesPanel,
                BorderLayout.CENTER);

        JPanel bottomPanel =
                new JPanel(
                        new BorderLayout(10, 0));

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
        boolean noValue = false;
        if (label == null) {
            noValue = true;
        }
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.weightx = 0;
        if (noValue) {
            constraints.gridwidth = 2;
        }

        if (!noValue) {
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
        }

        panel.add(
                valueLabel,
                constraints);

        constraints.gridy++;
    }

    private void addPropertyRowSeparator(
            JPanel panel,
            GridBagConstraints constraints) {

        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.weightx = 0;

        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);

        panel.add(
                separator,
                constraints);

        constraints.gridy++;
    }

    private void setItem(S3FileItem item) {

        if (item == null) {
            return;
        }

        nameValue.setText(
                item.getName());

        nameValue.setIcon(
                item.isFolder()
                        ? IconProvider.ICON_SYSTEM_CLOSED_FOLDER
                        : IconProvider.getFileTypeIcon(
                        item.getKey()));

        locationValue.setText(
                item.getBucket()
                        + "/"
                        + item.getKey());

        if (item.isFolder()) {

            // The exact S3 placeholder object will be checked asynchronously.
            typeValue.setText(
                    "Folder");

            sizeValue.setText(
                    "-");

            lastModifiedValue.setText(
                    "-");

            storageClassValue.setText(
                    "-");

            foldersValue.setText(
                    "Calculating...");

            filesValue.setText(
                    "Calculating...");

            totalSizeValue.setText(
                    "Calculating...");

            startStatusAnimation(
                    "Calculating");

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

    public void updateFolderType(
            boolean realFolder,
            Instant lastModified) {

        typeValue.setText(
                realFolder
                        ? "Folder - Real"
                        : "Folder - Virtual");

        lastModifiedValue.setText(
                realFolder
                        ? DateFormatter.format(lastModified)
                        : "-");
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

    public void setStatus(String status) {

        if (status == null || status.isBlank()) {
            stopStatusAnimation();
            statusLabel.setText("");
            return;
        }

        startStatusAnimation(status);
    }

    public void setCalculationCompleted() {
        stopStatusAnimation();
        statusLabel.setText("");
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


    private void setMultipleItems(
            List<S3FileItem> items) {

        if (items == null || items.isEmpty()) {
            nameValue.setText("-");
            nameValue.setIcon(null);
            typeValue.setText("-");
            locationValue.setText("-");
            sizeValue.setText("-");
            lastModifiedValue.setText("-");
            storageClassValue.setText("-");
            foldersValue.setText("-");
            filesValue.setText("-");
            totalSizeValue.setText("-");
            statusLabel.setText("");
            return;
        }

        long folderCount =
                items.stream()
                        .filter(S3FileItem::isFolder)
                        .count();

        long fileCount =
                items.size() - folderCount;

        StringBuilder name = new StringBuilder();

        if (folderCount > 0) {
            name.append(folderCount)
                    .append(folderCount == 1 ? " folder" : " folders");
        }

        if (folderCount > 0 && fileCount > 0) {
            name.append(" and ");
        }

        if (fileCount > 0) {
            name.append(fileCount)
                    .append(fileCount == 1 ? " file" : " files");
        }

        name.append(" selected");

        nameValue.setText(name.toString());
        nameValue.setIcon(null);

        if (folderCount == items.size()) {
            typeValue.setText("Folder");
        } else if (fileCount == items.size()) {
            typeValue.setText("File");
        } else {
            typeValue.setText("Mixed");
        }

        S3FileItem firstItem = items.get(0);

        String key = firstItem.getKey();

        if (firstItem.isFolder() && key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }

        int lastSlash = key.lastIndexOf('/');

        String parentPrefix =
                lastSlash >= 0
                        ? key.substring(0, lastSlash + 1)
                        : "";

        locationValue.setText(
                firstItem.getBucket()
                        + (parentPrefix.isEmpty()
                        ? ""
                        : "/" + parentPrefix));

        sizeValue.setText("-");
        lastModifiedValue.setText("-");
        storageClassValue.setText("-");

        foldersValue.setText("Calculating...");
        filesValue.setText("Calculating...");
        totalSizeValue.setText("Calculating...");

        startStatusAnimation(
                "Calculating properties of selected items");;
    }

    private void startStatusAnimation(String message) {

        stopStatusAnimation();

        statusBaseText = message;
        statusDotCount = 0;

        statusLabel.setText(statusBaseText);

        statusAnimationTimer = new Timer(100, e -> {
            statusDotCount = (statusDotCount + 1) % 30;

            statusLabel.setText(
                    statusBaseText + ".".repeat(statusDotCount));
        });

        statusAnimationTimer.start();
    }

    private void stopStatusAnimation() {

        if (statusAnimationTimer != null) {
            statusAnimationTimer.stop();
            statusAnimationTimer = null;
        }
    }
}