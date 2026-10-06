package com.company.s3explorer.ui.preferences;

import com.company.s3explorer.config.ApplicationSettings;
import com.company.s3explorer.config.ProxySettings;
import com.company.s3explorer.util.ProxyConfigurer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class PreferencesDialog
        extends JDialog {

    private final ApplicationSettings settings;

    private final ProxySettings proxySettings;

    private JComboBox<ProxySettings.Mode>
            proxyModeComboBox;

    private JPanel manualProxyPanel;

    private JTextField httpProxyField;

    private JTextField httpsProxyField;

    private JTextField noProxyField;

    private JCheckBox useAuthenticationCheckBox;

    private JPanel authenticationPanel;

    private JTextField usernameField;

    private JPasswordField passwordField;

    public PreferencesDialog(
            Window owner,
            ApplicationSettings settings) {

        super(
                owner,
                "Preferences",
                ModalityType.APPLICATION_MODAL);

        this.settings = settings;

        this.proxySettings =
                copyProxySettings(
                        settings.getProxySettings());

        buildUI();
        loadSettings();

        setMinimumSize(
                new Dimension(
                        600,
                        300));

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
                BorderLayout.NORTH);

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

        panel.add(
                manualProxyPanel,
                gbc);

        proxyModeComboBox.addActionListener(
                e -> updateProxyVisibility());

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

        useAuthenticationCheckBox =
                new JCheckBox(
                        "Use Authentication");

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        panel.add(
                useAuthenticationCheckBox,
                gbc);

        authenticationPanel =
                createAuthenticationPanel();

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        panel.add(
                authenticationPanel,
                gbc);

        useAuthenticationCheckBox
                .addActionListener(
                        e ->
                                updateAuthenticationVisibility());

        return panel;
    }

    private JPanel createAuthenticationPanel() {

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
                        "Username:"),
                gbc);

        usernameField =
                new JTextField();

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        panel.add(
                usernameField,
                gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;

        panel.add(
                new JLabel(
                        "Password:"),
                gbc);

        passwordField =
                new JPasswordField();

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;

        panel.add(
                passwordField,
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

        JButton saveButton =
                new JButton(
                        "Save");

        saveButton.addActionListener(
                e -> saveSettings());

        panel.add(
                cancelButton);

        panel.add(
                saveButton);

        return panel;
    }

    private void saveSettings() {

        ProxySettings.Mode mode =
                (ProxySettings.Mode)
                        proxyModeComboBox
                                .getSelectedItem();

        /*
         * Manual proxy configuration seçildiyse
         * Http Proxy veya Https Proxy alanlarından
         * en az biri doldurulmuş olmalı.
         */
        if (mode
                == ProxySettings.Mode
                .MANUAL_PROXY_CONFIGURATION) {

            String httpProxy =
                    httpProxyField
                            .getText()
                            .trim();

            String httpsProxy =
                    httpsProxyField
                            .getText()
                            .trim();

            if (httpProxy.isEmpty()
                    && httpsProxy.isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please enter at least one of " +
                                "Http Proxy or Https Proxy.",
                        "Invalid Proxy Settings",
                        JOptionPane.WARNING_MESSAGE);

                if (httpProxy.isEmpty()) {
                    httpProxyField.requestFocusInWindow();
                } else {
                    httpsProxyField.requestFocusInWindow();
                }

                return;
            }
        }

        /*
         * Authentication seçildiyse Username ve Password
         * alanlarının ikisi de doldurulmuş olmalı.
         */
        if (mode
                == ProxySettings.Mode
                .MANUAL_PROXY_CONFIGURATION
                && useAuthenticationCheckBox.isSelected()) {

            String username =
                    usernameField
                            .getText()
                            .trim();

            String password =
                    new String(
                            passwordField
                                    .getPassword())
                            .trim();

            if (username.isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please enter a Username.",
                        "Invalid Proxy Authentication",
                        JOptionPane.WARNING_MESSAGE);

                usernameField.requestFocusInWindow();

                return;
            }

            if (password.isEmpty()) {

                JOptionPane.showMessageDialog(
                        this,
                        "Please enter a Password.",
                        "Invalid Proxy Authentication",
                        JOptionPane.WARNING_MESSAGE);

                passwordField.requestFocusInWindow();

                return;
            }
        }

        /*
         * Validation başarılı.
         * Artık geçici ProxySettings değerlerini
         * güncelliyoruz.
         */
        proxySettings.setMode(
                mode);

        proxySettings.setHttpProxy(
                httpProxyField
                        .getText()
                        .trim());

        proxySettings.setHttpsProxy(
                httpsProxyField
                        .getText()
                        .trim());

        proxySettings.setNoProxy(
                noProxyField
                        .getText()
                        .trim());

        boolean useAuthentication =
                mode
                        == ProxySettings.Mode
                        .MANUAL_PROXY_CONFIGURATION
                        && useAuthenticationCheckBox.isSelected();

        proxySettings.setUseAuthentication(
                useAuthentication);

        if (useAuthentication) {
            proxySettings.setUsername(
                    usernameField
                            .getText()
                            .trim());

            proxySettings.setPassword(
                    new String(
                            passwordField
                                    .getPassword()));

        } else {
            /*
             * Authentication artık kullanılmıyorsa
             * eski kullanıcı adı ve şifreyi de temizle.
             */
            proxySettings.setUsername(null);
            proxySettings.setPassword(null);
        }

        /*
         * Sadece başarılı Save sonrasında
         * gerçek ApplicationSettings değiştirilir.
         */
        settings.setProxySettings(
                copyProxySettings(
                        proxySettings));

        ProxyConfigurer.configureSystemProxies(proxySettings);
        dispose();
    }

    private void loadSettings() {

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

        useAuthenticationCheckBox.setSelected(
                proxySettings.isUseAuthentication());

        usernameField.setText(
                valueOrEmpty(
                        proxySettings.getUsername()));

        passwordField.setText(
                valueOrEmpty(
                        proxySettings.getPassword()));

        updateProxyVisibility();
    }

    private void updateProxyVisibility() {

        boolean manual =
                proxyModeComboBox.getSelectedItem()
                        == ProxySettings.Mode
                        .MANUAL_PROXY_CONFIGURATION;

        manualProxyPanel.setVisible(
                manual);

        if (!manual) {

            useAuthenticationCheckBox
                    .setSelected(false);
        }

        updateAuthenticationVisibility();

        refreshDialog();
    }

    private void updateAuthenticationVisibility() {

        boolean visible =
                proxyModeComboBox.getSelectedItem()
                        == ProxySettings.Mode
                        .MANUAL_PROXY_CONFIGURATION
                        && useAuthenticationCheckBox
                        .isSelected();

        authenticationPanel.setVisible(
                visible);

        refreshDialog();
    }

    private void refreshDialog() {

        revalidate();
        repaint();

        pack();

        setMinimumSize(
                new Dimension(
                        600,
                        300));

        setLocationRelativeTo(
                getOwner());
    }

    private ProxySettings copyProxySettings(
            ProxySettings source) {

        ProxySettings copy =
                new ProxySettings();

        if (source == null) {
            return copy;
        }

        copy.setMode(
                source.getMode());

        copy.setHttpProxy(
                source.getHttpProxy());

        copy.setHttpsProxy(
                source.getHttpsProxy());

        copy.setNoProxy(
                source.getNoProxy());

        copy.setUseAuthentication(
                source.isUseAuthentication());

        copy.setUsername(
                source.getUsername());

        copy.setPassword(
                source.getPassword());

        return copy;
    }

    private String valueOrEmpty(
            String value) {

        return value == null
                ? ""
                : value;
    }
}