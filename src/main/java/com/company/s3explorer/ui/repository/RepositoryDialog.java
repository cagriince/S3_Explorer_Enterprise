package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.security.EncryptionConfigValidator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

public class RepositoryDialog extends JDialog {

    private JTextField nameField;
    private JTextField endpointField;
    private JTextField accessKeyField;
    private JPasswordField secretKeyField;
    private JTextArea externalBucketsArea;

    private JTextField encryptionTransformationField;
    private JTextField encryptionIvField;
    private JTextField encryptionKeyField;
    private JCheckBox useEncryptionCheckBox;

    private RepositoryDefinition repository;

    public RepositoryDialog(Window owner) {
        super(owner, ModalityType.APPLICATION_MODAL);

        initialize();

        setTitle("Add Repository");
    }

    public RepositoryDialog(
            Window owner,
            RepositoryDefinition repository) {

        this(owner);

        nameField.setText(
                repository.getName());

        endpointField.setText(
                repository.getEndpoint());

        accessKeyField.setText(
                repository.getAccessKey());

        secretKeyField.setText(
                repository.getSecretKey());

        if (repository.getExternalBuckets() != null) {

            externalBucketsArea.setText(
                    String.join(
                            System.lineSeparator(),
                            repository.getExternalBuckets()));
        }

        encryptionTransformationField.setText(
                repository.getEncryptionTransformation());

        encryptionIvField.setText(
                repository.getEncryptionIv());

        encryptionKeyField.setText(
                repository.getEncryptionKey());

        boolean useEncryption =
                repository.getEncryptionTransformation() != null
                        && !repository.getEncryptionTransformation().isBlank()
                        && repository.getEncryptionIv() != null
                        && !repository.getEncryptionIv().isBlank()
                        && repository.getEncryptionKey() != null
                        && !repository.getEncryptionKey().isBlank();

        useEncryptionCheckBox.setSelected(
                useEncryption);

        updateEncryptionFieldsState();

        setTitle("Edit Repository");
    }

    private void initialize() {

        setSize(
                500,
                500);

        setLocationRelativeTo(
                getOwner());

        setLayout(
                new BorderLayout());

        add(
                createFormPanel(),
                BorderLayout.CENTER);

        add(
                createButtonPanel(),
                BorderLayout.SOUTH);

        JDialog dialog = this;

        rootPane.getInputMap(
                        JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_ESCAPE,
                                0),
                        "ESCAPE_KEY");

        rootPane.getActionMap()
                .put(
                        "ESCAPE_KEY",
                        new AbstractAction() {

                            @Override
                            public void actionPerformed(
                                    ActionEvent e) {

                                dialog.dispose();
                            }
                        });
    }

    public RepositoryDefinition getRepository() {
        return repository;
    }

    private JPanel createFormPanel() {

        JPanel panel =
                new JPanel(
                        new GridBagLayout());

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.insets =
                new Insets(
                        5,
                        5,
                        5,
                        5);

        gbc.fill =
                GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel("Name"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        nameField =
                new JTextField();

        panel.add(
                nameField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel("Endpoint"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;

        endpointField =
                new JTextField();

        panel.add(
                endpointField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel("Access Key"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;

        accessKeyField =
                new JTextField();

        panel.add(
                accessKeyField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel("Secret Key"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;

        secretKeyField =
                new JPasswordField();

        panel.add(
                secretKeyField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        gbc.weighty = 1.0;
        gbc.fill =
                GridBagConstraints.BOTH;
        gbc.anchor =
                GridBagConstraints.NORTHWEST;

        panel.add(
                new JLabel("External Buckets"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        externalBucketsArea =
                new JTextArea(
                        5,
                        30);

        externalBucketsArea.setLineWrap(
                false);

        JScrollPane externalBucketsScroll =
                new JScrollPane(
                        externalBucketsArea);

        panel.add(
                externalBucketsScroll,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill =
                GridBagConstraints.HORIZONTAL;
        gbc.anchor =
                GridBagConstraints.WEST;

        useEncryptionCheckBox =
                new JCheckBox(
                        "Use Encryption");

        useEncryptionCheckBox.addActionListener(
                e -> updateEncryptionFieldsState());

        panel.add(
                useEncryptionCheckBox,
                gbc);

        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Encryption Transformation"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.weightx = 1.0;

        encryptionTransformationField =
                new JTextField();

        panel.add(
                encryptionTransformationField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Encryption IV"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.weightx = 1.0;

        encryptionIvField =
                new JTextField();

        panel.add(
                encryptionIvField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Encryption Key"),
                gbc);

        gbc.gridx = 1;
        gbc.gridy = 8;
        gbc.weightx = 1.0;

        encryptionKeyField =
                new JTextField();

        panel.add(
                encryptionKeyField,
                gbc);

        updateEncryptionFieldsState();

        return panel;
    }

    private JPanel createButtonPanel() {

        JPanel panel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                10,
                                10));

        JButton saveButton =
                new JButton("Save");

        JButton cancelButton =
                new JButton("Cancel");

        saveButton.addActionListener(
                e -> saveRepository());

        cancelButton.addActionListener(
                e -> dispose());

        panel.add(
                saveButton);

        panel.add(
                cancelButton);

        return panel;
    }

    private void saveRepository() {

        String name =
                nameField
                        .getText()
                        .trim();

        String endpoint =
                endpointField
                        .getText()
                        .trim();

        String accessKey =
                accessKeyField
                        .getText()
                        .trim();

        String secretKey =
                new String(
                        secretKeyField
                                .getPassword())
                        .trim();

        List<String> externalBuckets =
                Arrays.stream(
                                externalBucketsArea
                                        .getText()
                                        .split("\\R"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();

        String encryptionTransformation =
                encryptionTransformationField
                        .getText()
                        .trim();

        String encryptionIv =
                encryptionIvField
                        .getText()
                        .trim();

        String encryptionKey =
                encryptionKeyField
                        .getText()
                        .trim();

        if (name.isEmpty()
                || endpoint.isEmpty()
                || accessKey.isEmpty()
                || secretKey.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "All fields are required");

            return;
        }

        if (useEncryptionCheckBox.isSelected()
                && (encryptionTransformation.isEmpty()
                || encryptionIv.isEmpty()
                || encryptionKey.isEmpty())) {

            JOptionPane.showMessageDialog(
                    this,
                    "All encryption fields are required when encryption is enabled.");

            return;
        }

        if (useEncryptionCheckBox.isSelected()) {
            try {

                EncryptionConfigValidator.validate(
                        encryptionTransformation,
                        encryptionIv,
                        encryptionKey);

            } catch (IllegalArgumentException ex) {

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Invalid Encryption Configuration",
                        JOptionPane.ERROR_MESSAGE);

                return;
            }
        }
        
        try {

            repository =
                    new RepositoryDefinition();

            repository.setName(
                    name);

            repository.setEndpoint(
                    endpoint);

            repository.setAccessKey(
                    accessKey);

            repository.setSecretKey(
                    secretKey);

            repository.setExternalBuckets(
                    externalBuckets);

            repository.setEncryptionTransformation(
                    useEncryptionCheckBox.isSelected()
                            ? encryptionTransformation
                            : null);

            repository.setEncryptionIv(
                    useEncryptionCheckBox.isSelected()
                            ? encryptionIv
                            : null);

            repository.setEncryptionKey(
                    useEncryptionCheckBox.isSelected()
                            ? encryptionKey
                            : null);

            dispose();

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Error: " + ex.getMessage());
        }
    }

    private void updateEncryptionFieldsState() {

        boolean enabled =
                useEncryptionCheckBox.isSelected();

        encryptionTransformationField.setEnabled(
                enabled);

        encryptionIvField.setEnabled(
                enabled);

        encryptionKeyField.setEnabled(
                enabled);
    }
}