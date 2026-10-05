package com.company.s3explorer.ui.preferences;

import com.company.s3explorer.config.ApplicationSettings;
import com.company.s3explorer.config.ProxySettings;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class PreferencesDialog
        extends JDialog {

    private final ApplicationSettings settings;

    private JComboBox<ProxySettings.Mode> proxyModeComboBox;

    private JPanel manualProxyPanel;

    private JTextField httpProxyField;
    private JTextField httpsProxyField;
    private JTextField noProxyField;

    public PreferencesDialog(
            Window owner,
            ApplicationSettings settings) {

        super(
                owner,
                "Preferences",
                ModalityType.APPLICATION_MODAL);

        this.settings = settings;

        buildUI();
        loadSettings();

        setMinimumSize(
                new Dimension(
                        600,
                        400));

        setSize(
                650,
                450);

        setLocationRelativeTo(owner);
    }

    private void buildUI() {

        JPanel contentPanel =
                new JPanel(
                        new BorderLayout(
                                10,
                                10));

        contentPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        10,
                        10,
                        10));

        contentPanel.add(
                createPreferencesPanel(),
                BorderLayout.CENTER);

        contentPanel.add(
                createButtonPanel(),
                BorderLayout.SOUTH);

        setContentPane(
                contentPanel);
    }

    private JPanel createPreferencesPanel() {

        JPanel panel =
                new JPanel();

        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS));

        panel.add(
                createProxySettingsPanel());

        panel.add(
                Box.createVerticalGlue());

        return panel;
    }

    private JPanel createProxySettingsPanel() {

        JPanel panel =
                new JPanel(
                        new GridBagLayout());

        panel.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(
                                UIManager.getColor(
                                        "Component.borderColor")),
                        "Proxy Settings",
                        TitledBorder.LEFT,
                        TitledBorder.TOP));

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.insets =
                new Insets(
                        5,
                        5,
                        5,
                        5);

        gbc.anchor =
                GridBagConstraints.WEST;

        gbc.fill =
                GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Proxy Configuration:"),
                gbc);

        proxyModeComboBox =
                new JComboBox<>(
                        ProxySettings.Mode.values());

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        panel.add(
                proxyModeComboBox,
                gbc);

        manualProxyPanel =
                createManualProxyPanel();

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill =
                GridBagConstraints.HORIZONTAL;

        panel.add(
                manualProxyPanel,
                gbc);

        proxyModeComboBox.addActionListener(
                e -> updateManualProxyVisibility());

        return panel;
    }

    private JPanel createManualProxyPanel() {

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

        gbc.anchor =
                GridBagConstraints.WEST;

        gbc.fill =
                GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Http Proxy:"),
                gbc);

        httpProxyField =
                new JTextField();

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        panel.add(
                httpProxyField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Https Proxy:"),
                gbc);

        httpsProxyField =
                new JTextField();

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;

        panel.add(
                httpsProxyField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "No Proxy:"),
                gbc);

        noProxyField =
                new JTextField();

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;

        panel.add(
                noProxyField,
                gbc);

        return panel;
    }

    private JPanel createButtonPanel() {

        JPanel panel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT));

        JButton cancelButton =
                new JButton(
                        "Cancel");

        cancelButton.addActionListener(
                e -> dispose());

        JButton okButton =
                new JButton(
                        "OK");

        okButton.addActionListener(
                e -> saveSettings());

        panel.add(
                cancelButton);

        panel.add(
                okButton);

        return panel;
    }

    private void loadSettings() {

        ProxySettings proxySettings =
                settings.getProxySettings();

        if (proxySettings == null) {

            proxySettings =
                    new ProxySettings();

            settings.setProxySettings(
                    proxySettings);
        }

        proxyModeComboBox.setSelectedItem(
                proxySettings.getMode());

        httpProxyField.setText(
                valueOrEmpty(
                        proxySettings.getHttpProxy()));

        httpsProxyField.setText(
                valueOrEmpty(
                        proxySettings.getHttpsProxy()));

        noProxyField.setText(
                valueOrEmpty(
                        proxySettings.getNoProxy()));

        updateManualProxyVisibility();
    }

    private void updateManualProxyVisibility() {

        boolean manual =
                proxyModeComboBox.getSelectedItem()
                        == ProxySettings.Mode
                        .MANUAL_PROXY_CONFIGURATION;

        manualProxyPanel.setVisible(
                manual);

        manualProxyPanel.revalidate();
        manualProxyPanel.repaint();

        pack();

        setMinimumSize(
                new Dimension(
                        600,
                        300));
    }

    private void saveSettings() {

        ProxySettings proxySettings =
                settings.getProxySettings();

        if (proxySettings == null) {

            proxySettings =
                    new ProxySettings();

            settings.setProxySettings(
                    proxySettings);
        }

        proxySettings.setMode(
                (ProxySettings.Mode)
                        proxyModeComboBox
                                .getSelectedItem());

        proxySettings.setHttpProxy(
                httpProxyField.getText().trim());

        proxySettings.setHttpsProxy(
                httpsProxyField.getText().trim());

        proxySettings.setNoProxy(
                noProxyField.getText().trim());

        dispose();
    }

    private String valueOrEmpty(
            String value) {

        return value == null
                ? ""
                : value;
    }
}