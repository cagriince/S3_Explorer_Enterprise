package com.company.s3explorer.ui.repository;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.S3ClientFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class RepositoryDialog extends JDialog {
    private JTextField nameField;
    private JTextField endpointField;
    private JTextField accessKeyField;
    private JPasswordField secretKeyField;
    private RepositoryDefinition repository;

    public RepositoryDialog(Window owner) {
        super(owner, ModalityType.APPLICATION_MODAL);
        initialize();
        setTitle("Add Repository");
    }

    public RepositoryDialog(Window owner, RepositoryDefinition repository) {
        this(owner);
        nameField.setText(repository.getName());
        endpointField.setText(repository.getEndpoint());
        accessKeyField.setText(repository.getAccessKey());
        secretKeyField.setText(repository.getSecretKey());
        setTitle("Edit Repository");
    }

    private void initialize() {
        setSize(500, 220);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());

        add(createFormPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        JDialog dialog = this;
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_KEY");
        rootPane.getActionMap().put("ESCAPE_KEY", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose(); // Pencereyi kapatır ve kaynakları serbest bırakır
            }
        });
    }

    public RepositoryDefinition getRepository() {
        return repository;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5); // Hücreler arası boşluk
        gbc.fill = GridBagConstraints.HORIZONTAL; // Bileşenleri yatayda genişlet

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        panel.add(new JLabel("Name"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        nameField = new JTextField();
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        panel.add(new JLabel("Endpoint"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        endpointField = new JTextField();
        panel.add(endpointField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        panel.add(new JLabel("Access Key"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        accessKeyField = new JTextField();
        panel.add(accessKeyField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        panel.add(new JLabel("Secret Key"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        secretKeyField = new JPasswordField();
        panel.add(secretKeyField, gbc);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> saveRepository());
        cancelButton.addActionListener(e -> dispose());

        panel.add(saveButton);
        panel.add(cancelButton);

        return panel;
    }

    private void saveRepository() {
        String name = nameField.getText().trim();
        String endpoint = endpointField.getText().trim();
        String accessKey = accessKeyField.getText().trim();
        String secretKey = new String(secretKeyField.getPassword()).trim();

        if (name.isEmpty()
                || endpoint.isEmpty()
                || accessKey.isEmpty()
                || secretKey.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "All fields are required");

            return;
        }

        try {

            repository = new RepositoryDefinition();
            repository.setName(name);
            repository.setEndpoint(endpoint);
            repository.setAccessKey(accessKey);
            repository.setSecretKey(secretKey);

            dispose();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error: " + ex.getMessage());
        }
    }
}