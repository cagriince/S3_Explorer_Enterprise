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
    
    public MainFrame() {
        initialize();
    }

    private void initialize() {
        loadSettings();
        ProxyConfigurer.configureSystemProxies(settings.getProxySettings());
        buildDependencies();
        buildUI();

        setTitle("S3 Explorer");

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

        int savedThreadCount = settings.getTransferThreadCount();
        explorerPanel.selectThreadCount( savedThreadCount);
        
        String lastSelectedTheme = settings.getLastSelectedTheme();
        if (lastSelectedTheme != null) {
            explorerPanel.selectTheme(lastSelectedTheme);
        }
        else {
            explorerPanel.selectTheme(UIThemeManager.DEFAULT_THEME.name());
        }
        String lastSelectedRepository = settings.getLastSelectedRepository();
        String lastSelectedBucket = settings.getLastSelectedBucket();
        SwingUtilities.invokeLater(() -> {
                explorerPanel.selectRepository(repositoryManager.findById(lastSelectedRepository));
                explorerPanel.selectBucket(lastSelectedBucket);
            }
        );

        explorerPanel.setThreadCountSelectionListener(
                threadCount -> {

                    settings.setTransferThreadCount(
                            threadCount);

                    settingsStore.save(
                            settings);

                    transferEngine.setThreadCount(
                            threadCount);
                });
        
        explorerPanel.setThemeSelectionListener(
                theme -> {
                    settings.setLastSelectedTheme(theme.name());
                    settingsStore.save(settings);
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

        JMenuBar menuBar =
                new JMenuBar();

        // -------------------------------------------------
        // File
        // -------------------------------------------------

        JMenu fileMenu =
                new JMenu("File");

        JMenuItem exitItem =
                new JMenuItem("Exit");

        exitItem.addActionListener(
                e ->
                        dispatchEvent(
                                new WindowEvent(
                                        this,
                                        WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exitItem);

        // -------------------------------------------------
        // Repository
        // -------------------------------------------------

        JMenu repositoryMenu =
                new JMenu("Repository");

        JMenuItem repositoryManagerItem =
                new JMenuItem(
                        "Repository Manager");

        repositoryManagerItem.addActionListener(
                e ->
                        explorerPanel
                                .openRepositoryManager());

        repositoryMenu.add(
                repositoryManagerItem);

        // -------------------------------------------------
        // Settings
        // -------------------------------------------------

        JMenu settingsMenu =
                new JMenu("Settings");

        JMenuItem preferencesItem =
                new JMenuItem("Preferences");

        preferencesItem.addActionListener(
                e -> {

                    PreferencesDialog dialog =
                            new PreferencesDialog(
                                    this,
                                    settings);

                    dialog.setVisible(true);

                    settingsStore.save(
                            settings);
                });

        settingsMenu.add(
                preferencesItem);

        // -------------------------------------------------
        // Help
        // -------------------------------------------------

        JMenu helpMenu =
                new JMenu("Help");

        JMenuItem aboutItem =
                new JMenuItem("About");

        aboutItem.addActionListener(
                e ->
                        JOptionPane.showMessageDialog(
                                this,
                                "<html><font color=red size=6><b>GİB<br/><font color=blue>S3 </font><font color=black>Explorer</font></b></font></html>",
                                "About",
                                JOptionPane.INFORMATION_MESSAGE,
                                IconProvider.ICON_LOGO_96));

        helpMenu.add(aboutItem);

        // -------------------------------------------------

        menuBar.add(fileMenu);
        menuBar.add(repositoryMenu);
        menuBar.add(settingsMenu);
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
}