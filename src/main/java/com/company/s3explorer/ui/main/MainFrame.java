package com.company.s3explorer.ui.main;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.config.ApplicationSettings;
import com.company.s3explorer.config.ApplicationSettingsStore;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.S3ClientFactory;
import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.TransferEngine;
import com.company.s3explorer.ui.explorer.ExplorerPanel;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.preferences.PreferencesDialog;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.ui.transfer.TransferPanel;
import com.company.s3explorer.util.ProxyConfigurer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {
    private ExplorerPanel explorerPanel;
    private ActiveRepositoryContext activeRepositoryContext;
    private RepositoryManager repositoryManager;
    private S3ClientFactory clientFactory;
    private S3ClientManager clientManager;
    private TransferEngine transferEngine;
    private ApplicationSettingsStore settingsStore;
    private ApplicationSettings settings;
    private TransferPanel transferPanel;
    private JSplitPane split;

    private JMenuItem repositoryManagerMenuItem;
    private JMenuItem exitMenuItem;
    private JMenuItem preferencesMenuItem;
    private JMenuItem aboutMenuItem;
    private JMenuItem shortcutsMenuItem;

    public MainFrame() {
        initialize();
    }


    private void initialize() {
        loadSettings();
        ProxyConfigurer.configureSystemProxies(settings.getProxySettings());
        buildDependencies();
        buildUI();

        // Tema değiştiğinde MainFrame menü ikonlarını yenile.
        explorerPanel.setMenuIconUpdater(this::updateMenuIcons);

        setTitle("GİB S3 Explorer");

        /*
         * Eski ayar dosyalarıyla uyumluluk:
         * Önceki sürüm, büyütülmüş pencereyi -1 genişlik
         * ve -1 yükseklik olarak kaydediyordu.
         */
        boolean restoreMaximized =
                settings.isWindowMaximized()
                        || settings.getWindowWidth() <= 0
                        || settings.getWindowHeight() <= 0;

        int windowWidth = settings.getWindowWidth();
        int windowHeight = settings.getWindowHeight();

        if (windowWidth <= 0) {
            windowWidth = 1200;
            settings.setWindowWidth(windowWidth);
        }

        if (windowHeight <= 0) {
            windowHeight = 800;
            settings.setWindowHeight(windowHeight);
        }

        setSize(windowWidth, windowHeight);

        if (settings.getWindowX() >= 0
                && settings.getWindowY() >= 0) {

            setLocation(
                    settings.getWindowX(),
                    settings.getWindowY());

        } else {
            setLocationRelativeTo(null);
        }

        /*
         * Boyut ve konum belirlendikten sonra büyütülmüş
         * pencere durumunu geri yükle.
         */
        if (restoreMaximized) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        explorerPanel.setFolderEditModePreference(settings.isFolderEditMode());

        int savedThreadCount = settings.getTransferThreadCount();
        explorerPanel.selectThreadCount(savedThreadCount);

        String lastSelectedTheme = settings.getLastSelectedTheme();

        if (lastSelectedTheme != null) {
            explorerPanel.selectTheme(lastSelectedTheme);
        } else {
            explorerPanel.selectTheme(UIThemeManager.DEFAULT_THEME.name());
        }

        String lastSelectedRepository = settings.getLastSelectedRepository();
        String lastSelectedBucket = settings.getLastSelectedBucket();

        SwingUtilities.invokeLater(() -> {
            explorerPanel.selectRepository(
                    repositoryManager.findById(lastSelectedRepository));
            explorerPanel.selectBucket(lastSelectedBucket);
        });

        explorerPanel.setThreadCountSelectionListener(
                threadCount -> {
                    settings.setTransferThreadCount(threadCount);
                    settingsStore.save(settings);
                    transferEngine.setThreadCount(threadCount);
                });

        explorerPanel.setThemeSelectionListener(
                theme -> {
                    settings.setLastSelectedTheme(theme.name());
                    settingsStore.save(settings);
                    updateMenuIcons();
                });

        explorerPanel.setRepositorySelectionListener(
                repository -> {
                    settings.setLastSelectedRepository(repository.getId());
                    settings.setLastSelectedBucket(null);
                    settingsStore.save(settings);
                    //explorerPanel.updateActionStates();
                });

        explorerPanel.setBucketSelectionListener(
                bucket -> {
                    settings.setLastSelectedBucket(bucket);
                    settingsStore.save(settings);
                    //explorerPanel.updateActionStates();
                });

        explorerPanel.setFolderEditModeSelectionListener(
                enabled -> {
                    settings.setFolderEditMode(enabled);
                    settingsStore.save(settings);
                });

        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void loadSettings() {
        settingsStore = new ApplicationSettingsStore();
        settings = settingsStore.load();
    }

    private void buildDependencies() {
        activeRepositoryContext = new ActiveRepositoryContext();
        repositoryManager = new RepositoryManager();
        clientFactory = new S3ClientFactory();
        clientManager = new S3ClientManager(repositoryManager, clientFactory, activeRepositoryContext);
        transferEngine =
                new TransferEngine(
                        clientManager,
                        settings.getTransferThreadCount());
        transferPanel = new TransferPanel(transferEngine.getEventBus(), transferEngine.getTransferManager());

        explorerPanel =
                new ExplorerPanel(
                        activeRepositoryContext,
                        clientFactory,
                        transferEngine.getEventBus(),
                        transferEngine.getTransferManager(),
                        repositoryManager,
                        clientManager,
                        transferPanel);
    }

    private void buildUI() {

        JPanel root =
                new JPanel(
                        new BorderLayout());

        split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        explorerPanel,
                        transferPanel);

        split.setResizeWeight(0.75);

        root.add(
                split,
                BorderLayout.CENTER);

        setContentPane(root);

        setJMenuBar(
                createMenuBar());

        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosing(
                            WindowEvent e) {

                        saveApplicationState();
                    }
                });
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Repository Manager - top-level item without a submenu
        repositoryManagerMenuItem = new JMenuItem("Repository Manager");
        repositoryManagerMenuItem.setIcon(IconProvider.ICON_REPOSITORY);
        repositoryManagerMenuItem.setMnemonic(KeyEvent.VK_R);
        repositoryManagerMenuItem.addActionListener(
                e -> explorerPanel.openRepositoryManager());

        menuBar.add(repositoryManagerMenuItem);

        // File
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        explorerPanel.populateFileMenu(fileMenu);

        // Exit
        exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.setIcon(IconProvider.ICON_EXIT);
        exitMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_Q,
                        InputEvent.CTRL_DOWN_MASK));

        exitMenuItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Do you really want to exit the application?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                dispatchEvent(
                        new WindowEvent(
                                this,
                                WindowEvent.WINDOW_CLOSING));
            }
        });

        fileMenu.add(exitMenuItem);

        // Edit
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        explorerPanel.populateEditMenu(editMenu);
        editMenu.addSeparator();

        preferencesMenuItem = new JMenuItem("Preferences");
        preferencesMenuItem.setIcon(IconProvider.ICON_SETTINGS);
        preferencesMenuItem.addActionListener(e -> {
            PreferencesDialog dialog =
                    new PreferencesDialog(this, settings);

            dialog.setVisible(true);
            settingsStore.save(settings);
        });

        editMenu.add(preferencesMenuItem);

        // View
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        explorerPanel.populateViewMenu(viewMenu);

        // Go
        JMenu goMenu = new JMenu("Go");
        goMenu.setMnemonic(KeyEvent.VK_G);
        explorerPanel.populateGoMenu(goMenu);

        // Help
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        shortcutsMenuItem = new JMenuItem("Shortcuts");
        shortcutsMenuItem.setIcon(IconProvider.ICON_KEYBOARD);
        shortcutsMenuItem.addActionListener(
                e -> showKeyboardShortcutsDialog());

        helpMenu.add(shortcutsMenuItem);
        helpMenu.addSeparator();

        // About
        aboutMenuItem = new JMenuItem("About");
        aboutMenuItem.setIcon(IconProvider.ICON_ABOUT);
        aboutMenuItem.addActionListener(
                e -> JOptionPane.showMessageDialog(
                        this,
                        "<html><font color=red size=6><b>GİB<br/>"
                                + "<font color=blue>S3 </font>"
                                + "<font color=black>Explorer</font>"
                                + "</b></font></html>",
                        "About",
                        JOptionPane.INFORMATION_MESSAGE,
                        IconProvider.ICON_LOGO_96));

        helpMenu.add(aboutMenuItem);

        // Top-level menu order
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);
        menuBar.add(goMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void saveApplicationState() {

        boolean isMaximized =
                (getExtendedState() & JFrame.MAXIMIZED_BOTH)
                        == JFrame.MAXIMIZED_BOTH;

        settings.setWindowMaximized(isMaximized);

        /*
         * Pencere büyütülmüş durumdaysa mevcut ekran
         * boyutlarını normal pencere boyutlarının üzerine
         * yazma. Önceki normal pencere geometrisini koru.
         */
        if (!isMaximized) {

            settings.setWindowWidth(getWidth());
            settings.setWindowHeight(getHeight());

            settings.setWindowX(getX());
            settings.setWindowY(getY());
        }

        settings.setLastSelectedRepository(
                activeRepositoryContext.getActiveRepository() != null
                        ? activeRepositoryContext
                        .getActiveRepository()
                        .getId()
                        : null);

        settingsStore.save(settings);
    }

/*
    private void selectDefaultRepository() {
        RepositoryDefinition selected = repositoryManager.findByName(settings.getLastSelectedRepository());
        if (selected == null && !repositoryManager.getRepositories().isEmpty()) {
            selected = repositoryManager.getRepositories().get(0);
        }

        explorerPanel.setSelectedRepository(selected);
    }*/

    @Override
    public void dispose() {

        transferEngine.close();

        explorerPanel.shutdown();
        clientManager.close();

        super.dispose();
    }


    private void showKeyboardShortcutsDialog() {
        String[] columns = {
                "Action",
                "Shortcut"
        };

        Object[][] rows = {
                {"Open selected item", "Enter"},
                {"Select all items", "Ctrl + A"},
                {"Copy", "Ctrl + C"},
                {"Cut", "Ctrl + X"},
                {"Paste", "Ctrl + V"},
                {"Go to parent folder", "Backspace"},
                {"Delete selected items", "Delete"},
                {"Rename selected item", "F2"},
                {"Show properties", "Alt + Enter"},
                {"Navigate back", "Alt + Left"},
                {"Navigate forward", "Alt + Right"},
                {"Reload folder", "F5"},
                {"Exit", "Ctrl + Q"},
        };

        JTable table = new JTable(
                new javax.swing.table.DefaultTableModel(rows, columns) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                });

        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(230);

        table.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(140);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, 400));

        JOptionPane.showMessageDialog(
                this,
                scrollPane,
                "Keyboard Shortcuts",
                JOptionPane.PLAIN_MESSAGE);
    }



    private void updateMenuIcons() {
        if (repositoryManagerMenuItem != null) {
            repositoryManagerMenuItem.setIcon(IconProvider.ICON_REPOSITORY);
        }

        if (exitMenuItem != null) {
            exitMenuItem.setIcon(IconProvider.ICON_EXIT);
        }

        if (preferencesMenuItem != null) {
            preferencesMenuItem.setIcon(IconProvider.ICON_SETTINGS);
        }

        if (shortcutsMenuItem != null) {
            shortcutsMenuItem.setIcon(IconProvider.ICON_KEYBOARD);
        }

        if (aboutMenuItem != null) {
            aboutMenuItem.setIcon(IconProvider.ICON_ABOUT);
        }
    }
}