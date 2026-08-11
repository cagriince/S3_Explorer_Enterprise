package com.company.s3explorer.ui.main;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.config.ApplicationSettings;
import com.company.s3explorer.config.ApplicationSettingsStore;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.S3ClientFactory;
import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.TransferEngine;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.producer.ProducerExecutor;
import com.company.s3explorer.transfer.queue.TransferQueue;
import com.company.s3explorer.ui.explorer.ExplorerPanel;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.ui.transfer.TransferPanel;

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
/*        settings = settingsStore.load();
        String lastSelectedTheme = settings.getLastSelectedTheme();
        if (lastSelectedTheme != null) {
            try {
                UIManager.setLookAndFeel(UIThemeManager.getThemeClass(UIThemeManager.UIThemeConst.valueOf(lastSelectedTheme)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            IconProvider.reloadSystemIcons();
        }*/

        buildDependencies();
        buildUI();

        setTitle("S3 Explorer");
        setSize(settings.getWindowWidth(), settings.getWindowHeight());
        if (settings.getWindowX() >= 0 && settings.getWindowY() >= 0) {
            setLocation(settings.getWindowX(), settings.getWindowY());
        } else {
            setLocationRelativeTo(null);
            setSize(1200, 800);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

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
                explorerPanel.setDividerLocation(settings.getDividerLocationVertical());
                this.setDividerLocation(settings.getDividerLocationHorizontal());
                explorerPanel.selectRepository(repositoryManager.findByName(lastSelectedRepository));
                explorerPanel.selectBucket(lastSelectedBucket);
            }
        );

        explorerPanel.setThemeSelectionListener(
                theme -> {
                    settings.setLastSelectedTheme(theme.name());
                    settingsStore.save(settings);
                });

        explorerPanel.setRepositorySelectionListener(
                repository -> {
                    settings.setLastSelectedRepository(repository.getName());
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
        transferEngine = new TransferEngine(clientManager);
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
        JPanel root = new JPanel(new BorderLayout());

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, explorerPanel, transferPanel);
        //split.setResizeWeight(0.80);
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);

        addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        saveApplicationState();
                    }
                });
    }

    private void saveApplicationState() {
        boolean isMaximized = (this.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        if (isMaximized) {
            settings.setWindowWidth(-1);
            settings.setWindowHeight(-1);
        }
        else {
            settings.setWindowWidth(getWidth());
            settings.setWindowHeight(getHeight());
        }

        settings.setWindowX(getX());
        settings.setWindowY(getY());

        settings.setDividerLocationVertical(explorerPanel.getDividerLocation());
        settings.setDividerLocationHorizontal(this.getDividerLocation());
        settings.setLastSelectedRepository(activeRepositoryContext.getActiveRepository() != null ? activeRepositoryContext.getActiveRepository().getName() : null);

        settingsStore.save(settings);
    }

    public int getDividerLocation() {
        if (split == null) {
            return 800;
        }
        return split.getDividerLocation();
    }

    public void setDividerLocation(int location) {
        if (split == null) {
            return;
        }
        split.setDividerLocation(location);
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
        clientManager.close();
        super.dispose();
    }
}