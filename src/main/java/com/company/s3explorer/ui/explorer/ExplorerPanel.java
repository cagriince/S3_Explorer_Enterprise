package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.transfer.renderer.FileSizeRenderer;
import com.company.s3explorer.transfer.renderer.InstantRenderer;
import com.company.s3explorer.ui.action.ExplorerAction;
import com.company.s3explorer.ui.icons.IconProvider;
import com.company.s3explorer.ui.repository.RepositoryPanel;
import com.company.s3explorer.ui.theme.UITheme;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.ui.transfer.TransferPanel;
import com.company.s3explorer.util.S3Util;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ExplorerPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ExplorerPanel.class);

    private static final Integer[] FILE_TABLE_ROW_LIMITS = {
            100,
            250,
            500,
            1000,
            2000,
            5000,
            10000,
            20000,
            50000,
            100000,
            200000,
            500000,
            1000000,
            10000000
    };
    private static final Integer[] THREAD_COUNTS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            15, 20, 25, 30, 40, 50, 60, 80, 100
    };
    
    private final ExplorerRefreshScheduler refreshScheduler;
    private final Map<String, S3TreeNode> nodeCache = new HashMap<>();

    private JComboBox<UITheme> themeCombo;
    private JComboBox<RepositoryDefinition> repositoryCombo;
    private JLabel repositoryLabel;
    private JComboBox<String> bucketCombo;
    private JLabel bucketLabel;

    private JTree folderTree;
    private DefaultTreeModel treeModel;

    private JTable fileTable;
    private FileTableModel fileTableModel;
    private final AtomicLong fileLoadGeneration = new AtomicLong();
    private String currentFileBucket;
    private String currentFilePrefix;
    private String currentFileContinuationToken;
    private boolean currentFileHasMore;
    private boolean loadingMoreFiles;
    private LimitedFolderContent currentFolderFullContent;
    private String currentFolderFullContentBucket;
    private String currentFolderFullContentPrefix;
    
    private JComboBox<Integer> fileTableRowLimitCombo;
    private Consumer<Integer> fileTableRowLimitSelectionListener;
    
    private JComboBox<Integer> threadCountCombo;
    private Consumer<Integer> threadCountSelectionListener;

    private JPanel breadcrumbPanel;
    private JLabel fileFolderInfo;

    private JPopupMenu filePopup;
    private JSplitPane mainSplit;

    private File lastOpenedFolderToUpload;
    private File lastOpenedFolderToDownload;

    ExecutorService explorerPool = Executors.newFixedThreadPool(5);
    private final UIThemeManager themeManager;
    private final ActiveRepositoryContext context;
    private final S3ClientFactory clientFactory;
    private final TransferEventBus eventBus;
    private final TransferManager transferManager;
    private final RepositoryManager repositoryManager;
    private final S3ClientManager clientManager;

    private Consumer<UITheme> themeSelectionListener;
    private Consumer<RepositoryDefinition> repositorySelectionListener;
    private Consumer<String> bucketSelectionListener;
    private RepositoryDefinition pendingRepositorySelection;
    private String pendingBucketSelection;
    private boolean suppressBucketSelectionEvent;
    private boolean suppressThreadCountSelectionEvent;
    
    private final ExplorerClipboard clipboard = new ExplorerClipboard();

    private Action downloadAction;
    private Action deleteAction;
    private Action copyAction;
    private Action cutAction;
    private Action pasteAction;
    private Action uploadAction;
    private Action newFolderAction;
    private Action refreshAction;
    private Action manageRepositoryAction;
    private Action goToParentAction;

    public ExplorerPanel(
            ActiveRepositoryContext context,
            S3ClientFactory clientFactory,
            TransferEventBus eventBus,
            TransferManager transferManager,
            RepositoryManager repositoryManager,
            S3ClientManager clientManager,
            TransferPanel transferPanel) {

        this.context = context;
        this.clientFactory = clientFactory;
        this.eventBus = eventBus;
        this.transferManager = transferManager;
        this.repositoryManager = repositoryManager;
        this.clientManager = clientManager;
        this.themeManager = new UIThemeManager(this, transferPanel);

        refreshScheduler =
                new ExplorerRefreshScheduler(
                        this::refreshNode,
                        this::refreshCurrentTable);

        initialize();
    }

    private void initialize() {
        createActions();
        setLayout(new BorderLayout());
        add(createMainSplit(), BorderLayout.CENTER);
        bindEvents();
        defineShortCuts();
        fileTableRowLimitSelectionListener =
                selectedLimit -> {

                    log.debug(
                            "[FILE TABLE LIMIT CHANGED] limit={}",
                            selectedLimit);

                    reloadCurrentFileTable();
                };
        reloadRepositories();
        
        repositoryManager.addRepositoryChangeListener(
                this::onRepositoryChanged);
        
        eventBus.subscribe(this::onTransferEvent);
        eventBus.subscribe(
                new TransferListener() {

                    @Override
                    public void onTransferUpdated(
                            TransferRuntime runtime) {
                        onTransferEvent(runtime);
                    }

                    @Override
                    public void onTransferGroupCompleted(
                            TransferGroupCompletedEvent event) {

                        log.info("[EXPLORER LISTENER ENTERED] event={}", event);

                        ExplorerPanel.this
                                .onTransferGroupCompleted(event);
                    }
                });
    }

    private void createActions() {
        manageRepositoryAction = new ExplorerAction("Refresh", this::showRepositoryManager);
        refreshAction = new ExplorerAction("Refresh", this::loadBucketsAsync);
        uploadAction = new ExplorerAction("Upload", this::uploadFile);
        newFolderAction = new ExplorerAction("New Folder", this::createFolder);
        downloadAction = new ExplorerAction("Download", this::downloadSelected);
        deleteAction = new ExplorerAction("Delete", this::deleteSelected);
        copyAction = new ExplorerAction("Copy", this::copySelected);
        cutAction = new ExplorerAction("Cut", this::moveSelected);
        pasteAction = new ExplorerAction("Paste", this::pasteClipboard);
        goToParentAction = new ExplorerAction("GoToParent", this::goToParentFolder);
    }

    private void defineShortCuts() {
        // -------------------------------------------------
        // File Table
        // -------------------------------------------------

        InputMap inputMap = fileTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = fileTable.getActionMap();

        inputMap.put(
                KeyStroke.getKeyStroke("ENTER"),
                "openSelectedFileItem");
        actionMap.put(
                "openSelectedFileItem",
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        openSelectedFileItem();
                    }
                });

        inputMap.put(KeyStroke.getKeyStroke("control C"), "copy");
        actionMap.put("copy", copyAction);

        inputMap.put(KeyStroke.getKeyStroke("control X"), "move");
        actionMap.put("move", cutAction);

        inputMap.put(KeyStroke.getKeyStroke("control V"),"paste");
        actionMap.put("paste", pasteAction);

        // Backspace
        inputMap.put(KeyStroke.getKeyStroke("BACK_SPACE"),"explorerGoParent");
        actionMap.put("explorerGoParent", goToParentAction);

        // -------------------------------------------------
        // Tree
        // -------------------------------------------------

        InputMap treeInputMap = folderTree.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap treeActionMap = folderTree.getActionMap();
        treeInputMap.put(KeyStroke.getKeyStroke("BACK_SPACE"), "explorerGoParent");
        treeActionMap.put("explorerGoParent", goToParentAction);

        // -------------------------------------------------
        // ExplorerPanel
        // -------------------------------------------------

        InputMap panelInputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap panelActionMap = getActionMap();

        // Ctrl+V
        panelInputMap.put(KeyStroke.getKeyStroke("control V"),"explorerPaste");
        panelActionMap.put("explorerPaste",pasteAction);

        panelInputMap.put(KeyStroke.getKeyStroke("BACK_SPACE"),"explorerGoParent");
        panelActionMap.put("explorerGoParent", goToParentAction);
    }

    private JPanel createTopBar() {
        JPanel container = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 10, 5, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.2;
        repositoryLabel = new JLabel("Repository", SwingConstants.LEFT);
        repositoryLabel.setIconTextGap(14);
        container.add(repositoryLabel, gbc);

        repositoryCombo = new JComboBox<>();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.6;
        container.add(repositoryCombo, gbc);

        JButton manageRepositoriesBtn = createIconButton(manageRepositoryAction);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0.2;
        container.add(manageRepositoriesBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.2;
        bucketLabel = new JLabel("Bucket", SwingConstants.LEFT);
        bucketLabel.setIconTextGap(10);
        container.add(bucketLabel, gbc);

        bucketCombo = new JComboBox<>();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.6;
        container.add(bucketCombo, gbc);

        JButton refreshBtn = createIconButton(refreshAction);
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 0.2;
        container.add(refreshBtn, gbc);

        return container;
    }

    private JButton createIconButton(Action action) {
        JButton button = new JButton(action);
        button.setToolTipText((String) action.getValue(Action.NAME));
        button.setHideActionText(true);
        return button;
    }

    private JPanel createSeparator() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.setPreferredSize(new Dimension(20, 25));

        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;

        wrapper.add(sep, gbc);
        return wrapper;
    }

    private JSplitPane createMainSplit() {
        S3TreeNode root = new S3TreeNode(S3TreeNode.ROOT_PREFIX, S3TreeNode.ROOT_PREFIX, S3TreeNode.ROOT_PREFIX);
        folderTree = new JTree(root);
        nodeCache.put(S3TreeNode.ROOT_PREFIX, root);
        fileTable = new JTable(new DefaultTableModel(
                new Object[]{"Name", "Size", "Last Modified"}, 0
        ));
        treeModel = (DefaultTreeModel) folderTree.getModel();

        folderTree.addTreeWillExpandListener(
                new TreeWillExpandListener() {

                    @Override
                    public void treeWillExpand(TreeExpansionEvent event) {
                        loadChildren((S3TreeNode) event.getPath().getLastPathComponent());
                    }

                    @Override
                    public void treeWillCollapse(TreeExpansionEvent event) {
                    }
                });
        FolderTreeCellRenderer treeRenderer = new FolderTreeCellRenderer();
        folderTree.setCellRenderer(treeRenderer);
        setFolderTreeLeafIcon();


        fileTableModel = new FileTableModel();
        fileTable = createFileTable(fileTableModel);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(createTopBar(), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(folderTree), BorderLayout.CENTER);

        JScrollPane tableScrollPane = new JScrollPane(fileTable);
        installPopup(tableScrollPane.getViewport());
        installPopup(fileTable);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newFolderBtn = createIconButton(newFolderAction);
        JButton uploadBtn = createIconButton(uploadAction);
        JButton deleteBtn = createIconButton(deleteAction);
        JButton downloadBtn = createIconButton(downloadAction);
        JButton copyBtn = createIconButton(copyAction);
        JButton cutBtn =createIconButton(cutAction);
        JButton pasteBtn = createIconButton(pasteAction);
        buttonPanel.add(newFolderBtn);
        buttonPanel.add(uploadBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(downloadBtn);
        buttonPanel.add(createSeparator());
        buttonPanel.add(copyBtn);
        buttonPanel.add(cutBtn);
        buttonPanel.add(pasteBtn);

        JPanel themePanel =
                new JPanel(
                        new GridBagLayout());

        GridBagConstraints c =
                new GridBagConstraints();

        c.insets =
                new Insets(10, 5, 5, 5);

        /*
         * Max line count ComboBox
         */
        fileTableRowLimitCombo =
                new JComboBox<>(FILE_TABLE_ROW_LIMITS);

        fileTableRowLimitCombo.setSelectedItem(500);

        fileTableRowLimitCombo.setToolTipText(
                "Max Line Count");

        fileTableRowLimitCombo.addActionListener(e -> {

            Integer selected =
                    (Integer)
                            fileTableRowLimitCombo
                                    .getSelectedItem();

            if (selected == null) {
                return;
            }

            if (fileTableRowLimitSelectionListener != null) {
                fileTableRowLimitSelectionListener.accept(
                        selected);
            }
        });
        themePanel.add(
                fileTableRowLimitCombo,
                c);
        
        /*
         * Thread count ComboBox
         */
        threadCountCombo = new JComboBox<>(THREAD_COUNTS);
        threadCountCombo.setSelectedItem(15);
        threadCountCombo.setToolTipText(
                "Thread Count");
        threadCountCombo.addActionListener(e -> {

            if (suppressThreadCountSelectionEvent) {
                return;
            }

            Integer selected =
                    (Integer)
                            threadCountCombo
                                    .getSelectedItem();

            if (selected == null) {
                return;
            }

            if (threadCountSelectionListener != null) {
                threadCountSelectionListener.accept(
                        selected);
            }
        });

        themePanel.add(
                threadCountCombo,
                c);

        /*
         * Theme ComboBox
         */
        themeCombo =
                new JComboBox<>() {

                    @Override
                    public void setSelectedIndex(
                            int index) {

                        UITheme theme =
                                this.getItemAt(index);

                        if (theme.isDisabled()) {
                            return;
                        }

                        super.setSelectedIndex(
                                index);
                    }
                };
        themeCombo.setToolTipText(
                "Theme");

        themeManager.getThemes()
                .forEach(
                        themeCombo::addItem);

        c.insets =
                new Insets(
                        10,
                        5,
                        5,
                        10);

        themePanel.add(
                themeCombo,
                c);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonPanel, BorderLayout.WEST);
        topPanel.add(themePanel, BorderLayout.EAST);

        JPanel topButtonPanel = new JPanel(new BorderLayout());
        topButtonPanel.add(topPanel, BorderLayout.NORTH);

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        //breadcrumbPanel.setPreferredSize(new Dimension(0,40));

        JPanel bottomButtonPanel = new JPanel(new BorderLayout());
        bottomButtonPanel.setPreferredSize(new Dimension(0,40));
        bottomButtonPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        bottomButtonPanel.add(breadcrumbPanel, BorderLayout.CENTER);

        fileFolderInfo = new JLabel("");
        JPanel fileFolderInfoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        fileFolderInfoPanel.add(fileFolderInfo);
        bottomButtonPanel.add(fileFolderInfoPanel, BorderLayout.EAST);

        topButtonPanel.add(bottomButtonPanel, BorderLayout.SOUTH);

        JPanel fileTablePanel = new JPanel(new BorderLayout());
        fileTablePanel.add(topButtonPanel, BorderLayout.NORTH);
        fileTablePanel.add(tableScrollPane, BorderLayout.CENTER);

        mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                fileTablePanel);

        createPopupMenu();

        return mainSplit;
    }

    public void setFolderTreeLeafIcon() {
        DefaultTreeCellRenderer cellRenderer = (DefaultTreeCellRenderer) folderTree.getCellRenderer();
        cellRenderer.setLeafIcon(cellRenderer.getClosedIcon());
    }

    public void setButtonIcons() {
        repositoryLabel.setIcon(IconProvider.ICON_REPOSITORY);
        bucketLabel.setIcon(IconProvider.ICON_BUCKET);
        setActionIcon(manageRepositoryAction, IconProvider.ICON_SETTINGS);
        setActionIcon(refreshAction, IconProvider.ICON_REFRESH);
        setActionIcon(uploadAction, IconProvider.ICON_UPLOAD);
        setActionIcon(newFolderAction, IconProvider.ICON_CREATE_FOLDER);
        setActionIcon(downloadAction, IconProvider.ICON_DOWNLOAD);
        setActionIcon(deleteAction, IconProvider.ICON_DELETE);
        setActionIcon(copyAction, IconProvider.ICON_COPY);
        setActionIcon(cutAction, IconProvider.ICON_CUT);
        setActionIcon(pasteAction, IconProvider.ICON_PASTE);
    }

    private void setActionIcon(Action action, Icon newIcon) {
        action.putValue(Action.LARGE_ICON_KEY, newIcon);
    }

    private JTable createFileTable(FileTableModel fileTableModel) {
        JTable fileTable = new JTable(fileTableModel);
        TableColumn hidden = fileTable.getColumnModel().getColumn(0);
        hidden.setMinWidth(0);
        hidden.setMaxWidth(0);
        hidden.setPreferredWidth(0);
        hidden.setResizable(false);
        fileTable.getColumnModel().getColumn(1).setCellRenderer(new FileTableCellRenderer());
        fileTable.getColumnModel().getColumn(2).setCellRenderer(new FileSizeRenderer());
        fileTable.getColumnModel().getColumn(3).setCellRenderer(new InstantRenderer());

        FileTableRowSorter sorter = new FileTableRowSorter(fileTableModel);
        fileTable.setRowSorter(sorter);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.getTableHeader()
                .addMouseListener(
                        new MouseAdapter() {

                            @Override
                            public void mouseClicked(
                                    MouseEvent e) {

                                int viewColumn =
                                        fileTable
                                                .columnAtPoint(
                                                        e.getPoint());

                                if (viewColumn < 0) {
                                    return;
                                }

                                int modelColumn =
                                        fileTable
                                                .convertColumnIndexToModel(
                                                        viewColumn);

                                /*
                                 * Folder/File kolonu özel:
                                 * Mevcut davranışı bozma.
                                 */
                                if (modelColumn == 0) {
                                    return;
                                }

                                SwingUtilities.invokeLater(() -> {

                                    FileTableSortSpec sortSpec =
                                            getCurrentFileSortSpec();

                                    log.info(
                                            "[FILE SORT CHANGED] column={} order={}",
                                            sortSpec.getColumn(),
                                            sortSpec.isAscending()
                                                    ? "ASC"
                                                    : "DESC");

                                    reloadCurrentFileTable();
                                });
                            }
                        });

        final javax.swing.table.TableCellRenderer originalRenderer = fileTable.getTableHeader().getDefaultRenderer();
        fileTable.getTableHeader().setDefaultRenderer(new javax.swing.table.TableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                                                                    boolean isSelected, boolean hasFocus, int row, int column) {

                java.awt.Component c = originalRenderer.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (c instanceof javax.swing.JLabel) {
                    javax.swing.JLabel label = (javax.swing.JLabel) c;
                    label.setIcon(null);

                    if (table.getRowSorter() != null) {
                        java.util.List<? extends javax.swing.RowSorter.SortKey> sortKeys = table.getRowSorter().getSortKeys();

                        for (javax.swing.RowSorter.SortKey key : sortKeys) {
                            if (key.getColumn() == column && column != 0) {
                                if (key.getSortOrder() == javax.swing.SortOrder.ASCENDING) {
                                    label.setIcon(javax.swing.UIManager.getIcon("Table.ascendingSortIcon"));
                                } else if (key.getSortOrder() == javax.swing.SortOrder.DESCENDING) {
                                    label.setIcon(javax.swing.UIManager.getIcon("Table.descendingSortIcon"));
                                }
                            }
                        }
                    }
                }
                return c;
            }
        });
        fileTable.getRowSorter().toggleSortOrder(1);
        fileTable.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) {
                            openSelectedFileItem();
                        }
                    }
                });
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionStates();
            }
        });

        return fileTable;
    }

    private void installPopup(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
        });
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), fileTable);
        int row = fileTable.rowAtPoint(p);
        if (row < 0) {
            fileTable.clearSelection();
        }
        else {
            if (!fileTable.isRowSelected(row)) {
                fileTable.setRowSelectionInterval(row, row);
            }
        }

        updateActionStates();
        filePopup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void createPopupMenu() {
        filePopup = new JPopupMenu();
        JMenuItem createFolderMenu = new JMenuItem(newFolderAction);
        JMenuItem uploadMenu = new JMenuItem(uploadAction);
        JMenuItem downloadMenu = new JMenuItem(downloadAction);
        JMenuItem deleteMenu = new JMenuItem(deleteAction);
        JMenuItem copyMenu = new JMenuItem(copyAction);
        JMenuItem cutMenu = new JMenuItem(cutAction);
        JMenuItem pasteMenu = new JMenuItem(pasteAction);

        filePopup.add(createFolderMenu);
        filePopup.add(uploadMenu);
        filePopup.add(downloadMenu);
        filePopup.add(deleteMenu);
        filePopup.addSeparator();
        filePopup.add(copyMenu);
        filePopup.add(cutMenu);
        filePopup.add(pasteMenu);
        filePopup.addPopupMenuListener(
                new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                        pasteMenu.setEnabled(!clipboard.isEmpty());
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    }

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent e) {
                    }
                });
    }

    public void updateActionStates() {
        boolean folderSelected = this.getSelectedFolderNode() != null;
        boolean hasSelection = folderSelected && (fileTable.getSelectedRowCount() > 1 || (fileTable.getSelectedRowCount() == 1 && !fileTableModel.getItem(fileTable.convertRowIndexToModel(fileTable.getSelectedRow())).isParentFolder()));
        boolean hasClipboard = folderSelected && !clipboard.isEmpty();

        newFolderAction.setEnabled(folderSelected);
        uploadAction.setEnabled(folderSelected);

        downloadAction.setEnabled(hasSelection);
        deleteAction.setEnabled(hasSelection);
        copyAction.setEnabled(hasSelection);
        cutAction.setEnabled(hasSelection);
        pasteAction.setEnabled(hasClipboard);
    }

    private void deleteObject(S3FileItem item) {
        if (item.isParentFolder()) {
            return;
        }

        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        try {
            if (item.isFolder()) {
                transferManager.submitFolderDelete(item.getRepositoryName(), bucket, item.getKey());
            }
            else {
                transferManager.submitDelete(item.getRepositoryName(), bucket, item.getKey(), item.getSize());
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage()));
        }
    }

    public void loadRepositoriesAsync() {
        explorerPool.submit(() -> {
            try {
                List<RepositoryDefinition> repositories = repositoryManager.getRepositories();
                SwingUtilities.invokeLater(() -> {
                    repositoryCombo.removeAllItems();
                    repositoryCombo.addItem(RepositoryDefinition.EMPTY_REPOSITORY);
                    repositories.forEach(repositoryCombo::addItem);

                    if (pendingRepositorySelection != null) {
                        repositoryCombo.setSelectedItem(pendingRepositorySelection);
                        pendingRepositorySelection = null;
                    }
                    else {
                        repositoryCombo.setSelectedItem(RepositoryDefinition.EMPTY_REPOSITORY);
                    }
                });
            } catch (Exception ex) {
                log.error("Explorer operation failed", ex);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage()));
            }
        });
    }

    public void loadBucketsAsync() {

        /*
         * Refresh başlamadan önce gerçekten aktif olan bucket'ı
         * kaydet.
         */
        final String previousBucket =
                pendingBucketSelection != null
                        ? pendingBucketSelection
                        : getCurrentBucket();

        explorerPool.submit(() -> {

            try {

                /*
                 * ComboBox'tan seçili repository'yi al.
                 */
                RepositoryDefinition selectedRepository =
                        (RepositoryDefinition)
                                repositoryCombo.getSelectedItem();

                if (selectedRepository == null
                        || selectedRepository ==
                        RepositoryDefinition.EMPTY_REPOSITORY) {

                    log.debug(
                            "[BUCKET LOAD] no repository selected");

                    return;
                }

                /*
                 * RepositoryManager'dan güncel repository
                 * tanımını al.
                 *
                 * RepositoryDialog'daki external bucket
                 * değişiklikleri burada görülecek.
                 */
                RepositoryDefinition repository =
                        repositoryManager.findByName(
                                selectedRepository.getName());

                if (repository == null
                        || repository ==
                        RepositoryDefinition.EMPTY_REPOSITORY) {

                    log.warn(
                            "[BUCKET LOAD] repository not found: {}",
                            selectedRepository.getName());

                    return;
                }

                log.debug(
                        "[BUCKET LOAD START] repository={} previousBucket={} externalBuckets={}",
                        repository.getName(),
                        previousBucket,
                        repository.getExternalBuckets());

                /*
                 * Gerçek S3 bucket'larını al.
                 */
                List<String> s3Buckets;

                try {

                    s3Buckets =
                            getService().listBuckets();

                }
                catch (Exception ex) {

                    /*
                     * ListBuckets yetkisi yoksa external bucket'larla
                     * devam et.
                     */
                    log.warn(
                            "[BUCKET LOAD] ListBuckets failed; using external buckets only",
                            ex);

                    s3Buckets =
                            Collections.emptyList();
                }

                /*
                 * S3 bucket'ları + external bucket'lar.
                 */
                Set<String> allBuckets =
                        new LinkedHashSet<>();

                allBuckets.addAll(s3Buckets);

                allBuckets.addAll(
                        repository.getExternalBuckets());

                SwingUtilities.invokeLater(() -> {

                    suppressBucketSelectionEvent = true;

                    String selectedBucket = null;

                    try {

                        /*
                         * ComboBox'ı yeniden doldur.
                         */
                        bucketCombo.removeAllItems();

                        for (String bucket :
                                allBuckets) {

                            bucketCombo.addItem(bucket);
                        }

                        /*
                         * Önce mevcut bucket'ı korumaya çalış.
                         */
                        if (previousBucket != null
                                && allBuckets.contains(
                                previousBucket)) {

                            bucketCombo.setSelectedItem(
                                    previousBucket);
                        }

                        /*
                         * Mevcut bucket artık yoksa
                         * ilk bucket'ı seç.
                         */
                        else if (bucketCombo.getItemCount() > 0) {

                            bucketCombo.setSelectedIndex(0);
                        }

                        selectedBucket =
                                (String)
                                        bucketCombo.getSelectedItem();

                        log.debug(
                                "[BUCKET LOAD RESULT] repository={} buckets={} previous={} selected={}",
                                repository.getName(),
                                allBuckets,
                                previousBucket,
                                selectedBucket);

                    }
                    finally {

                        suppressBucketSelectionEvent = false;

                        pendingBucketSelection = null;
                    }

                    /*
                     * -------------------------------------------------
                     * KRİTİK NOKTA
                     * -------------------------------------------------
                     *
                     * Eğer bucket değişmediyse:
                     *
                     *     TREE'ye dokunma
                     *     FILE TABLE'a dokunma
                     *
                     * Sadece ComboBox yenilenmiş olsun.
                     */
                    if (Objects.equals(
                            previousBucket,
                            selectedBucket)) {

                        log.debug(
                                "[BUCKET LOAD] bucket unchanged={} - tree/table refresh skipped",
                                selectedBucket);

                        return;
                    }

                    /*
                     * Buraya ancak:
                     *
                     * - ilk bucket yükleniyorsa
                     * - aktif bucket silinmişse
                     * - gerçekten başka bucket seçilmişse
                     *
                     * geliyoruz.
                     */
                    if (selectedBucket != null) {

                        log.debug(
                                "[BUCKET LOAD] bucket changed {} -> {} - loading explorer",
                                previousBucket,
                                selectedBucket);

                        loadRootFolders(
                                selectedBucket);
                    }
                });

            }
            catch (Exception ex) {

                log.error(
                        "[BUCKET LOAD] failed",
                        ex);

                SwingUtilities.invokeLater(() ->
                        pendingBucketSelection = null);
            }
        });
    }
    
    public void loadRootFolders(String bucket) {
        explorerPool.submit(() -> {
            java.util.List<String> folders = getService().listFolders(bucket, S3TreeNode.ROOT_PREFIX);

            S3TreeNode root = new S3TreeNode(bucket, bucket, S3TreeNode.ROOT_PREFIX);
            nodeCache.clear();
            nodeCache.put(root.getFullPrefix(), root);

            for (String folder : folders) {
                String displayName = S3Util.extractFolderName(folder);
                S3TreeNode child = new S3TreeNode(displayName, bucket, folder);
                nodeCache.put(folder, child);

                child.add(new S3TreeNode(S3TreeNode.LOADING, bucket, S3TreeNode.ROOT_PREFIX));
                root.add(child);
            }

            SwingUtilities.invokeLater(() -> {
                treeModel.setRoot(root);
                folderTree.setSelectionRow(0);
                loadFiles(bucket, S3TreeNode.ROOT_PREFIX);
                updateBreadcrumb(S3TreeNode.ROOT_PREFIX);
                updateActionStates();
            });
        });
    }

    private void loadFiles(
            String bucket,
            String prefix) {

        long generation =
                fileLoadGeneration.incrementAndGet();

        currentFileBucket = bucket;
        currentFilePrefix = prefix;

        setFileTableLoading(true);

        int fileLimit =
                getSelectedFileTableRowLimit();

        FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        /*
         * Bu klasörün tamamı daha önce alınmış mı?
         */
        if (isFullContentCached(
                bucket,
                prefix)) {

            applyCachedFolderContent(
                    bucket,
                    prefix,
                    fileLimit,
                    sortSpec,
                    generation);

            return;
        }

        /*
         * Cache yoksa mevcut S3 akışı.
         */
        CompletableFuture
                .supplyAsync(
                        () -> getService()
                                .listFolderWithLimit(
                                        bucket,
                                        prefix,
                                        fileLimit,
                                        sortSpec),
                        explorerPool)
                .thenAccept(content -> {

                    SwingUtilities.invokeLater(() -> {

                        if (generation !=
                                fileLoadGeneration.get()) {
                            return;
                        }

                        /*
                         * Eğer bütün dosyaları aldıysak
                         * cache'e koy.
                         */
                        if (!content.fileLimitReached()) {

                            currentFolderFullContent =
                                    content;

                            currentFolderFullContentBucket =
                                    bucket;

                            currentFolderFullContentPrefix =
                                    prefix;
                        }

                        applyLimitedFolderContent(
                                bucket,
                                prefix,
                                content);

                        setFileTableLoading(false);
                    });
                })
                .exceptionally(ex -> {

                    log.error(
                            "Explorer operation failed",
                            ex);

                    SwingUtilities.invokeLater(() -> {

                        if (generation !=
                                fileLoadGeneration.get()) {
                            return;
                        }

                        setFileTableLoading(false);

                        JOptionPane.showMessageDialog(
                                this,
                                "File list could not be loaded");
                    });

                    return null;
                });
    }

    private void applyFilePage(
            String bucket,
            String prefix,
            FolderContentPage page,
            boolean append) {

        List<S3FileItem> rows =
                new ArrayList<>();

        if (!append
                && !"".equals(prefix)) {

            rows.add(
                    new S3FileItem(
                            this.getCurrentRepository()
                                    .getName(),
                            bucket,
                            prefix + "/" + S3FileItem.PARENT_FOLDER_NAME,
                            0,
                            null,
                            true));
        }

        rows.addAll(
                page.folders()
                        .stream()
                        .map(folder ->
                                new S3FileItem(
                                        this.getCurrentRepository()
                                                .getName(),
                                        bucket,
                                        folder,
                                        0,
                                        null,
                                        true))
                        .toList());

        rows.addAll(
                page.files()
                        .stream()
                        .filter(object ->
                                !object.key()
                                        .endsWith("/"))
                        .map(object ->
                                new S3FileItem(
                                        this.getCurrentRepository()
                                                .getName(),
                                        bucket,
                                        object.key(),
                                        object.size(),
                                        object.lastModified(),
                                        false))
                        .toList());

        if (append) {
            fileTableModel.addFiles(rows);
        }
        else {
            fileTableModel.setFiles(rows);
        }

        currentFileContinuationToken =
                page.continuationToken();

        currentFileHasMore =
                page.hasMore();

        long folderCount =
                fileTableModel
                        .getItems()
                        .stream()
                        .filter(
                                S3FileItem::
                                        isFolderButNotParent)
                        .count();

        long fileCount =
                fileTableModel
                        .getItems()
                        .stream()
                        .filter(
                                S3FileItem::isFile)
                        .count();

        String suffix =
                currentFileHasMore
                        ? " — more available"
                        : "";

        fileFolderInfo.setText(
                fileCount
                        + " file(s) and "
                        + folderCount
                        + " folder(s)"
                        + suffix);
    }

    private void bindEvents() {
        themeCombo.addActionListener(e -> {
            UITheme theme = (UITheme) themeCombo.getSelectedItem();
            themeManager.changeTheme(theme);
            if (themeSelectionListener != null) {
                themeSelectionListener.accept(theme);
            }
        });

        repositoryCombo.addActionListener(e -> {
            RepositoryDefinition repository = this.getCurrentRepository();
            if (repository == null) {
                return;
            }

            if (repositorySelectionListener != null) {
                repositorySelectionListener.accept(repository);
            }

            setSelectedRepository(repository);
        });

        bucketCombo.addActionListener(e -> {
            if (suppressBucketSelectionEvent) {
                return;
            }
            
            String bucket = this.getCurrentBucket();
            if (bucket == null) {
                return;
            }

            if (bucketSelectionListener != null) {
                bucketSelectionListener.accept(bucket);
            }

            loadRootFolders(bucket);
        });

        folderTree.addTreeSelectionListener(e -> {
            String bucket = this.getCurrentBucket();
            if (bucket == null) {
                return;
            }

            String prefix = this.getCurrentPrefix();

            loadChildren(this.getSelectedFolderNode());
            loadFiles(bucket, prefix);
            updateBreadcrumb(prefix);
            //updateActionStates();
        });
    }

    public UIThemeManager getThemeManager() {
        return themeManager;
    }

    private S3ExplorerService getService() {
        RepositoryDefinition repo = context.getActiveRepository();
        if (repo == null) {
            throw new IllegalStateException("No active repository selected");
        }

        return new S3ExplorerService(clientManager.getClient(context.getActiveRepository()));
    }

    public void setSelectedRepository(RepositoryDefinition repository) {
        repositoryCombo.setSelectedItem(repository);
        context.setActiveRepository(repository);
        this.reloadBuckets();
    }

    public void reloadRepositories() {
        repositoryCombo.removeAllItems();
        loadRepositoriesAsync();
    }

    public void reloadBuckets() {

        String previousBucket = getCurrentBucket();
        pendingBucketSelection = previousBucket;
        
        bucketCombo.removeAllItems();

        S3TreeNode root =
                new S3TreeNode(
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX);

        treeModel.setRoot(root);
        nodeCache.clear();
        nodeCache.put(
                root.getFullPrefix(),
                root);

        treeModel.reload();
        updateBreadcrumb(
                S3TreeNode.ROOT_PREFIX);
        updateActionStates();

        if (!context.hasActiveRepository()) {
            return;
        }

        pendingBucketSelection =
                previousBucket;

        loadBucketsAsync();
    }
    
    private void loadChildren(
            S3TreeNode parentNode) {

        loadChildren(parentNode, false);
    }

    private void loadChildren(
            S3TreeNode parentNode,
            boolean forceRefresh) {

        if (parentNode == null) {
            return;
        }

        if (!forceRefresh
                && parentNode.getChildCount() > 0
                && !((S3TreeNode)
                parentNode.getChildAt(0))
                .isLoading()) {

            return;
        }

        explorerPool.submit(() -> {

            String bucket =
                    this.getCurrentBucket();

            log.debug(
                    "[TREE LOAD START] thread={} bucket={} prefix={} forceRefresh={}",
                    Thread.currentThread().getName(),
                    bucket,
                    parentNode.getFullPrefix(),
                    forceRefresh);

            List<String> folders =
                    getService()
                            .listFolders(
                                    bucket,
                                    parentNode.getFullPrefix());

            log.debug(
                    "[TREE LOAD RESULT] thread={} bucket={} prefix={} folders={}",
                    Thread.currentThread().getName(),
                    bucket,
                    parentNode.getFullPrefix(),
                    folders.size());

            SwingUtilities.invokeLater(() -> {

                log.debug(
                        "[TREE APPLY START] thread={} prefix={} folders={}",
                        Thread.currentThread().getName(),
                        parentNode.getFullPrefix(),
                        folders.size());

                Enumeration<?> children =
                        parentNode.children();

                while (children.hasMoreElements()) {

                    removeFromCache(
                            (S3TreeNode)
                                    children.nextElement());
                }

                parentNode.removeAllChildren();

                for (String folder : folders) {

                    String displayName =
                            S3Util.extractFolderName(folder);

                    S3TreeNode child =
                            new S3TreeNode(
                                    displayName,
                                    bucket,
                                    folder);

                    nodeCache.put(
                            folder,
                            child);

                    child.add(
                            new S3TreeNode(
                                    S3TreeNode.LOADING,
                                    bucket,
                                    S3TreeNode.ROOT_PREFIX));

                    parentNode.add(child);
                }

                treeModel.reload(parentNode);

                log.debug(
                        "[TREE APPLY DONE] thread={} prefix={} childCount={}",
                        Thread.currentThread().getName(),
                        parentNode.getFullPrefix(),
                        parentNode.getChildCount());
            });
        });
    }

    private S3FileItem getSelectedFileItem() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }

        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return fileTableModel.getItem(modelRow);
    }

    private void openSelectedFileItem() {
        S3FileItem item = getSelectedFileItem();
        if (item == null) {
            return;
        }

        if (item.isFolder()) {
            if (item.isParentFolder()) {
                goToParentFolder();
            }
            else {
                navigateToFolder(item);
            }
            
            SwingUtilities.invokeLater(() -> {
                fileTable.requestFocusInWindow();
            });
        } else {
            downloadSelected();
        }
    }

    private void navigateToFolder(S3FileItem item) {
        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        String targetPrefix = (item.isParentFolder() ? S3Util.extractParentPrefix(item.getKey()) : item.getKey());
        S3TreeNode root = (S3TreeNode) treeModel.getRoot();

        TreePath path = findNodePath(root, targetPrefix);
        if (path != null) {
            folderTree.expandPath(path);
            folderTree.setSelectionPath(path);
            folderTree.scrollPathToVisible(path);
        } else {
            //loadFiles(bucket, targetPrefix);
        }
    }

    private TreePath findNodePath(
            S3TreeNode root,
            String targetPrefix) {

        if (root.getFullPrefix().equals(targetPrefix)) {
            return new TreePath(root.getPath());
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            Object child = root.getChildAt(i);
            if (child instanceof S3TreeNode node) {
                TreePath result = findNodePath(node, targetPrefix);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private void startDownload(S3FileItem item, Path destination) {
        if (item.isParentFolder()) {
            return;
        }

        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        try {
            if (item.isFolder()) {
                transferManager.submitFolderDownload(item.getRepositoryName(), bucket, item.getKey(), destination);
            } else {
                transferManager.submitDownload(item.getRepositoryName(), bucket, item.getKey(), destination, item.getSize());
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage()));
        }
    }

    private void createFolder() {
        String folderName = JOptionPane.showInputDialog(this, "Folder Name");
        if (folderName == null || folderName.isBlank()) {
            return;
        }

        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        String repositoryName = this.getCurrentRepository().getName();
        String prefix = getCurrentPrefix();
        String folderKey = prefix + folderName + "/";
        explorerPool.submit(() -> {
            try {
                transferManager.submitCreateFolder(repositoryName, bucket, folderKey, folderKey);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage()));
            }
        });
    }

    private void selectNodeByPrefix( String prefix) {
        S3TreeNode root = (S3TreeNode) treeModel.getRoot();
        TreePath path = findNodePath(root, prefix);
        if (path == null) {
            return;
        }

        folderTree.expandPath(path);
        folderTree.setSelectionPath(path);
        folderTree.scrollPathToVisible(path);
    }

    private void uploadFile() {
        String repositoryName = this.getCurrentRepository().getName();
        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (lastOpenedFolderToUpload != null) {
            chooser.setCurrentDirectory(lastOpenedFolderToUpload);
        }
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String prefix = getCurrentPrefix();

        File[] files = chooser.getSelectedFiles();
        for (File file : files) {
            String objectKey = prefix + file.getName();

            try {
                if (file.isFile()) {
                    lastOpenedFolderToUpload = file.getParentFile();
                    transferManager.submitUpload(repositoryName, bucket, objectKey, file.toPath(), file.length());
                } else {
                    lastOpenedFolderToUpload = file;
                    transferManager.submitFolderUpload(repositoryName, bucket, prefix, file.toPath());
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage()));
            }
        }
    }

    public void setThemeSelectionListener(Consumer<UITheme> listener) {
        this.themeSelectionListener = listener;
    }

    public void setRepositorySelectionListener(Consumer<RepositoryDefinition> listener) {
        this.repositorySelectionListener = listener;
    }

    public void setBucketSelectionListener(Consumer<String> listener) {
        this.bucketSelectionListener = listener;
    }

    public void selectTheme(String themeName) {
        this.themeCombo.setSelectedItem(themeManager.getThemeByName(themeName));
    }

    public void selectRepository(RepositoryDefinition repository) {
        pendingRepositorySelection = repository;
        repositoryCombo.setSelectedItem(repository);
    }

    public void selectBucket(String bucketName) {
        pendingBucketSelection = bucketName;
        if (bucketName == null) {
            return;
        }
        bucketCombo.setSelectedItem(bucketName);
    }

    public void onTransferEvent(
            TransferRuntime runtime) {

        if (runtime == null) {
            return;
        }

        if (runtime.getStatus()
                != TransferStatus.COMPLETED) {

            return;
        }

        TransferTask task =
                runtime.getTask();

        if (task == null) {
            return;
        }

        Set<RefreshTreeNode> affectedPrefixes =
                task.getAffectedPrefixes();

        /*
         * File Table'ın yenilenmesi gerekiyorsa
         * doğrudan loadFiles() çağırma.
         *
         * Scheduler bunu debounce edecektir.
         */
        if (task.isAffectsObjectList()) {

            refreshScheduler
                    .scheduleCurrentTableRefresh();
        }

        /*
         * Folder Tree refresh.
         */
        if (task.isAffectsFolderTree()
                && affectedPrefixes != null
                && !affectedPrefixes.isEmpty()) {

            Set<RefreshTreeNode> parentPrefixes =
                    new HashSet<>();

            for (RefreshTreeNode affected :
                    affectedPrefixes) {

                String prefix =
                        affected.prefix();

                String parentPrefix =
                        getParentPrefix(prefix);

                parentPrefixes.add(
                        new RefreshTreeNode(
                                parentPrefix,
                                affected.operation()));
            }

            refreshScheduler.scheduleRefresh(
                    parentPrefixes);
        }
    }

    private void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {

        TransferGroup group =
                event.getGroup();

        log.debug(
                "[EXPLORER GROUP COMPLETED] {}",
                group.getDisplayName());

        if (!event.isSuccessful()) {

            log.debug("[EXPLORER GROUP COMPLETED] not successful");

            return;
        }

        String prefix =
                event.getPrefix();

        String parentPrefix =
                getParentPrefix(prefix);

        log.debug(
                "[EXPLORER REFRESH] prefix={} parent={}",
                prefix,
                parentPrefix);

        refreshScheduler.scheduleRefresh(
                List.of(
                        new RefreshTreeNode(
                                parentPrefix,
                                RefreshTreeOperation.DELETE)));

        if (Objects.equals(
                parentPrefix,
                currentFilePrefix)) {

            refreshScheduler
                    .scheduleCurrentTableRefresh();
        }
    }

    private void selectAndLoadPrefix(
            String bucket,
            String prefix) {

        S3TreeNode root =
                (S3TreeNode) treeModel.getRoot();

        S3TreeNode node =
                findNodeByPrefix(
                        root,
                        prefix);

        if (node == null) {

            loadFiles(bucket, prefix);
            updateBreadcrumb(prefix);
            return;
        }

        TreePath path =
                new TreePath(node.getPath());

        folderTree.setSelectionPath(path);
        folderTree.scrollPathToVisible(path);

        loadFiles(bucket, prefix);
        updateBreadcrumb(prefix);
        updateActionStates();
    }

    private S3TreeNode findNodeByPrefix(S3TreeNode root, String prefix) {
        if (prefix.equals(root.getFullPrefix())) {
            return root;
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            S3TreeNode child = (S3TreeNode) root.getChildAt(i);
            if (prefix.startsWith(child.getFullPrefix())) {
                S3TreeNode result = findNodeByPrefix(child, prefix);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private Set<String> findNodeChildren(S3TreeNode node) {
        Set<String> children = new HashSet<>();

        for (int i = 0; i < node.getChildCount(); i++) {
            S3TreeNode child = (S3TreeNode) node.getChildAt(i);
            children.add(child.getFullPrefix());
        }

        return children;
    }
/*
    private void addFolderNode(String parentPrefix, String newFolderPrefix) {
        S3TreeNode root = (S3TreeNode) treeModel.getRoot();
        S3TreeNode parent = findNodeByPrefix(root, parentPrefix);
        if (parent == null) {
            return;
        }

        String displayName = S3Util.extractFolderName(newFolderPrefix);
        S3TreeNode child = new S3TreeNode(displayName, parent.getBucket(), newFolderPrefix);
        nodeCache.put(newFolderPrefix, child);
        child.add(new S3TreeNode(S3TreeNode.LOADING, parent.getBucket(), ""));
        parent.add(child);
        treeModel.reload(parent);
    }*/

 /*   private void addFolderNode(String parentPrefix, String newFolderPrefix, boolean recursiveAdd) {
        S3TreeNode root = (S3TreeNode) treeModel.getRoot();
        S3TreeNode parent = findNodeByPrefix(root, parentPrefix);
        if (parent == null) {
            return;
        }

        if (recursiveAdd) {
            // folder upload
            if (!findNodeChildren(parent).contains(newFolderPrefix)) {
                if (newFolderPrefix.startsWith(parent.getFullPrefix())) {
                    String subFolders = newFolderPrefix.substring(parent.getFullPrefix().length());
                    String firstSubFolder = subFolders.substring(0, subFolders.indexOf("/"));
                    String folderToCreate = newFolderPrefix + firstSubFolder + "/";

                    S3TreeNode child = new S3TreeNode(firstSubFolder, folderToCreate);
                    nodeCache.put(folderToCreate, child);
                    child.add(new DefaultMutableTreeNode("Loading..."));
                    parent.add(child);
                    treeModel.reload(parent);
                }
            }
        }
        else {
            String displayName = extractFolderName(newFolderPrefix);
            S3TreeNode child = new S3TreeNode(displayName, newFolderPrefix);
            nodeCache.put(newFolderPrefix, child);
            child.add(new DefaultMutableTreeNode("Loading..."));
            parent.add(child);
            treeModel.reload(parent);
        }
    }*/

    private void removeFolderNode(String folderPrefix) {
        S3TreeNode root = (S3TreeNode) treeModel.getRoot();
        S3TreeNode node = findNodeByPrefix(root, folderPrefix);
        if (node == null) {
            return;
        }
        if (node.getParent() == null) {
            return;
        }

        treeModel.removeNodeFromParent(node);
    }

    private void refreshCurrentTable() {
        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        loadFiles(bucket, getCurrentPrefix());
        updateActionStates();
    }

    public void updateBreadcrumb(String prefix) {
        if (prefix == null) {
            prefix = getCurrentPrefix();
        }

        breadcrumbPanel.removeAll();

        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        addBreadcrumbButton(bucket, S3TreeNode.ROOT_PREFIX);
        if (prefix != null && !prefix.isBlank()) {
            String[] parts = prefix.split("/");

            StringBuilder current = new StringBuilder();
            for (String part : parts) {
                if (part.isBlank()) {
                    continue;
                }

                current.append(part).append("/");
                addBreadcrumbButton(part, current.toString());
            }
        }

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    private void addBreadcrumbButton(String text, String prefix) {
        JButton button;
        if (!S3TreeNode.ROOT_PREFIX.equals(prefix)) {
            button = new JButton(text + " /");
        }
        else {
            button = new JButton(" /", IconProvider.ICON_SYSTEM_CLOSED_FOLDER);
        }
        button.setBackground(new Color(70, 130, 180));
        Color fgColor = button.getForeground();

        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(5, 5, 5, 5));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                Color contrastFGColor = getContrastColor(fgColor);
                button.setForeground(contrastFGColor);
                button.setBorder(new LineBorder(contrastFGColor, 5)); // Koyu gri kenarlık*/
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
                button.setForeground(fgColor);
                button.setBorder(new EmptyBorder(5, 5, 5, 5));
            }
        });

        button.addActionListener(e -> navigateToPrefix(prefix));
        breadcrumbPanel.add(button);
    }

    public Color getContrastColor(Color color) {
        double yiq = ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) / 1000;
        return (yiq >= 128) ? Color.BLACK : Color.WHITE;
    }

    private void navigateToPrefix(String prefix) {
        String bucket = this.getCurrentBucket();
        if (bucket == null) {
            return;
        }

        S3TreeNode root = (S3TreeNode) treeModel.getRoot();
        S3TreeNode node = findNodeByPrefix(root, prefix);

        if (node != null) {
            TreePath treePath = new TreePath(node.getPath());
            folderTree.setSelectionPath( treePath);
            folderTree.scrollPathToVisible(treePath);
        }

        loadFiles(bucket, prefix);
    }

    private void removeFromCache(S3TreeNode node) {
        if (node.isLoading()) {
            // Root'un prefix'i aynı olduğundan hata veriyor.
            return;
        }

        nodeCache.remove(node.getFullPrefix());

        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            removeFromCache((S3TreeNode) children.nextElement());
        }
    }
    /*
        public void refreshNode(String prefix) {
            DefaultMutableTreeNode node = nodeCache.get(prefix);
            if (node == null) {
                refreshTree();
                return;
            }
    
            reloadChildren(node);
        }
    
        private void reloadChildren(DefaultMutableTreeNode node) {
            log.debug("Reload Tree Node: {}", node);
            S3TreeNode treeNode = (S3TreeNode) node;
            Enumeration<?> children = node.children();
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
                removeFromCache(child);
            }
            node.removeAllChildren();
    
            List<String> folders = getService().listFolders(treeNode.getBucket(), treeNode.getFullPrefix());
            for (String folder : folders) {
                String displayName = extractFolderName(folder);
                DefaultMutableTreeNode child = new S3TreeNode(displayName, treeNode.getBucket(), folder);
                node.add(child);
                nodeCache.put(folder, child);
            }
    
            treeModel.reload(node);
            if (treeNode.getParent() == null) {
                // root olunca selection'ı kaybediyor
                folderTree.setSelectionRow(0);
            }
            loadFiles(treeNode.getBucket(), ((S3TreeNode) node).getFullPrefix());
        }
    */
    private void clearChildren(S3TreeNode parent) {
        Enumeration<?> e = parent.children();
        while (e.hasMoreElements()) {
            S3TreeNode child = (S3TreeNode) e.nextElement();
            removeFromCache(child);
        }

        parent.removeAllChildren();
    }

    private void attachChildren(S3TreeNode parentNode, List<String> prefixes) {
        for (String prefix : prefixes) {
            attachChild(parentNode, prefix);
        }
    }

    private void attachChild(S3TreeNode parentNode, String prefix) {
        String displayName = S3Util.extractFolderName(prefix);
        S3TreeNode childNode = new S3TreeNode(displayName, parentNode.getBucket(), prefix);
        parentNode.add(childNode);
        nodeCache.put(childNode.getFullPrefix(), childNode);
    }

    private void removeChild(S3TreeNode parentNode, S3TreeNode childNode) {
        if (!childNode.getParent().equals(parentNode)) {
            log.warn("Parent node mismatch while removing child");
        }
        parentNode.remove(childNode);
        nodeCache.remove(childNode.getFullPrefix());
    }

    private void reloadChildren(S3TreeNode parent) {
        clearChildren(parent);
        List<String> prefixes = getService().listFolders(parent.getBucket(), parent.getFullPrefix());
        attachChildren(parent, prefixes);
        treeModel.reload(parent);
    }

    private void refreshNode(
            RefreshTreeNode request) {

        String prefix = request.prefix();

        log.debug(
                "[EXPLORER TREE REFRESH] prefix={} operation={}",
                prefix,
                request.operation());

        S3TreeNode node =
                nodeCache.get(prefix);

        log.debug(
                "[EXPLORER TREE REFRESH NODE] prefix={} node={} childCount={}",
                prefix,
                node,
                node == null ? -1 : node.getChildCount());

        if (node == null) {

            log.debug("[EXPLORER TREE REFRESH NODE] NODE NOT FOUND");

            return;
        }

        loadChildren(node, true);
    }

    private void copySelected() {
        List<S3FileItem> items = getSelectedItems();
        if (items.isEmpty()) {
            return;
        }

        clipboard.copy(items);
        updateActionStates();
    }

    private void moveSelected() {
        List<S3FileItem> items = getSelectedItems();
        if (items.isEmpty()) {
            return;
        }

        clipboard.move(items);
        updateActionStates();
    }

    private void pasteClipboard() {
        if (clipboard.isEmpty()) {
            return;
        }

        String targetBucket = this.getCurrentBucket();
        String targetPrefix = this.getCurrentPrefix();

        for (S3FileItem item : clipboard.getItems()) {
            String targetKey = S3Util.combineKey(targetPrefix, item.getName());

            // Aynı klasöre yapıştırmayı engelle
            if (item.getBucket().equals(targetBucket) && item.getKey().equals(targetKey)) {
                continue;
            }

            if (clipboard.getOperation() == ExplorerClipboard.Operation.COPY) {
                submitCopy(item, targetBucket, targetKey);
            } else {
                submitMove(item, targetBucket, targetKey);
            }
        }

        if (clipboard.getOperation() == ExplorerClipboard.Operation.MOVE) {
            clipboard.clear();
        }

        updateActionStates();
    }

    private void submitCopy(S3FileItem item, String targetBucket, String targetKey) {
        if (item.isFolder()) {
            transferManager.submitFolderCopy(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    this.getCurrentRepository().getName(),
                    targetBucket,
                    targetKey);

            return;
        }

        if (item.getKey().equals(targetKey) && item.getBucket().equals(targetBucket)) {
            return;
        }

        if (exists(targetKey)) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Dosya mevcut. Üzerine yazılsın mı?");
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        transferManager.submitCopy(
                item.getRepositoryName(),
                item.getBucket(),
                item.getKey(),
                this.getCurrentRepository().getName(),
                targetBucket,
                targetKey,
                item.getSize());
    }

    private void submitMove(S3FileItem item, String targetBucket, String targetKey) {
        if (item.isFolder()) {
            transferManager.submitFolderMove(
                    item.getRepositoryName(),
                    item.getBucket(),
                    item.getKey(),
                    this.getCurrentRepository().getName(),
                    targetBucket,
                    targetKey);

            return;
        }

        if (item.getKey().equals(targetKey) && item.getBucket().equals(targetBucket)) {
            return;
        }

        if (exists(targetKey)) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Dosya mevcut. Üzerine yazılsın mı?");
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        transferManager.submitMove(
                item.getRepositoryName(),
                item.getBucket(),
                item.getKey(),
                this.getCurrentRepository().getName(),
                targetBucket,
                targetKey,
                item.getSize());
    }

    private List<S3FileItem> getSelectedItems() {
        int[] viewRows = fileTable.getSelectedRows();
        List<S3FileItem> items = new ArrayList<>();
        for (int row : viewRows) {
            S3FileItem item = fileTableModel.getItem(fileTable.convertRowIndexToModel(row));
            if (!item.isParentFolder()) {
                items.add(item);
            }
        }

        return items;
    }

    private RepositoryDefinition getCurrentRepository() {
        return (RepositoryDefinition) repositoryCombo.getSelectedItem();
    }

    private String getCurrentBucket() {
        return (String) bucketCombo.getSelectedItem();
    }

    private S3TreeNode getSelectedFolderNode() {
        return (S3TreeNode) folderTree.getLastSelectedPathComponent();
    }

    private String getCurrentPrefix() {
        S3TreeNode node = getSelectedFolderNode();
        if (node == null) {
            return S3TreeNode.ROOT_PREFIX;
        }

        return node.getFullPrefix();
    }

    private boolean exists(String key) {
        return fileTableModel.getItems()
                .stream()
                .anyMatch(i -> i.getKey().equals(key));
    }

    public void downloadSelected() {
        List<S3FileItem> items = getSelectedItems();
        if (items.isEmpty()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        if (lastOpenedFolderToDownload != null) {
            chooser.setCurrentDirectory(lastOpenedFolderToDownload);
        }
        //chooser.setSelectedFile(new File(extractFileName(item.getKey())));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = chooser.getSelectedFile().toPath();
        for (S3FileItem item : items) {
            startDownload(item, destination);
        }

        /*
                for (S3FileItem item : items) {
            startDownload(item, new File(extractFileName(item.getKey()), destination.toFile()));
        }
         */
        lastOpenedFolderToDownload = destination.toFile();
    }

    public void deleteSelected() {
        List<S3FileItem> items = getSelectedItems();
        if (items.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (S3FileItem item : items) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(item.getKey());
        }
        String message = null;
        if (items.size() == 1) {
            message = "Delete " + sb.toString() + " ?";
        }
        else {
            message = "Delete followings?\n" + sb.toString();
        }
        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Confirm",
                JOptionPane.YES_NO_OPTION);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        for (S3FileItem item : items) {
            deleteObject(item);
        }
    }

    private void showRepositoryManager() {
        RepositoryPanel panel =
                new RepositoryPanel(
                        repositoryManager,
                        null,
                        context,
                        clientFactory);

        JDialog dialog =
                new JDialog(
                        S3Util.getMainFrameAncestor(this),
                        "Repositories",
                        true);

        dialog.setContentPane(panel);
        dialog.setSize(800, 500);
        dialog.setLocationRelativeTo(this);

        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE_KEY");
        panel.getActionMap().put("ESCAPE_KEY", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        dialog.setVisible(true);

        // change combobox model accordingly if any change done in repository dialog
        RepositoryDefinition currentRepository = this.getCurrentRepository();
        List<RepositoryDefinition> repositoryList = repositoryManager.getRepositories();
        DefaultComboBoxModel<RepositoryDefinition> model = (DefaultComboBoxModel<RepositoryDefinition>) repositoryCombo.getModel();

        // delete from combo if not found in the list
        for (int i = model.getSize() - 1; i >= 1; i--) {
            RepositoryDefinition currentItem = model.getElementAt(i);
            if (!repositoryList.contains(currentItem)) {
                if (currentItem.equals(currentRepository)) {
                    this.setSelectedRepository(RepositoryDefinition.EMPTY_REPOSITORY);
                }
                model.removeElementAt(i);
            }
        }

        // add to combo if new
        for (RepositoryDefinition def : repositoryList) {
            if (model.getIndexOf(def) == -1) {
                model.addElement(def);
            }
        }
    }

    private void setFileTableLoading(boolean loading) {
        fileTable.setEnabled(!loading);

        if (loading) {
            fileTable.clearSelection();
            fileTableModel.clearAndRepaint();
        }
    }

    private void goToParentFolder() {
        S3TreeNode selectedNode = (S3TreeNode) folderTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return;
        }

        S3TreeNode parent = (S3TreeNode) selectedNode.getParent();
        if (parent == null) {
            return;
        }

        TreePath parentPath = new TreePath(parent.getPath());
        folderTree.setSelectionPath(parentPath);
        folderTree.scrollPathToVisible(parentPath);

        SwingUtilities.invokeLater(() ->
                fileTable.requestFocusInWindow()
        );
    }

    private String getParentPrefix(String prefix) {

        if (prefix == null || prefix.isBlank()) {
            return S3TreeNode.ROOT_PREFIX;
        }

        String normalized =
                prefix.endsWith("/")
                        ? prefix.substring(0, prefix.length() - 1)
                        : prefix;

        int index = normalized.lastIndexOf('/');

        if (index < 0) {
            return S3TreeNode.ROOT_PREFIX;
        }

        return normalized.substring(0, index + 1);
    }

    private RepositoryDefinition getFreshSelectedRepository() {
        RepositoryDefinition selected =
                (RepositoryDefinition) repositoryCombo.getSelectedItem();

        if (selected == null) {
            return null;
        }

        RepositoryDefinition fresh =
                repositoryManager.findByName(selected.getName());

        if (fresh == null
                || fresh == RepositoryDefinition.EMPTY_REPOSITORY) {
            return selected;
        }

        return fresh;
    }

    private void onRepositoryChanged(
            RepositoryDefinition changedRepository) {

        if (changedRepository == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            RepositoryDefinition currentRepository =
                    (RepositoryDefinition)
                            repositoryCombo.getSelectedItem();

            if (currentRepository == null
                    || currentRepository ==
                    RepositoryDefinition.EMPTY_REPOSITORY) {
                return;
            }

            /*
             * Başka repository değiştiyse
             * Explorer'a dokunma.
             */
            if (!Objects.equals(
                    currentRepository.getName(),
                    changedRepository.getName())) {

                return;
            }

            log.info(
                    "[EXPLORER REPOSITORY CHANGED] repository={}",
                    changedRepository.getName());

            /*
             * Mevcut bucket'ı sakla.
             */
            pendingBucketSelection =
                    getCurrentBucket();

            /*
             * Repository ComboBox'a dokunma.
             *
             * Sadece bucket listesini güncelle.
             */
            loadBucketsAsync();
        });
    }

    public void setThreadCountSelectionListener(
            Consumer<Integer> listener) {

        this.threadCountSelectionListener =
                listener;
    }

    public int getSelectedThreadCount() {

        Integer value =
                (Integer)
                        threadCountCombo
                                .getSelectedItem();

        return value == null
                ? 15
                : value;
    }

    public void selectThreadCount(
            int threadCount) {

        suppressThreadCountSelectionEvent = true;

        try {

            threadCountCombo.setSelectedItem(
                    threadCount);

        }
        finally {

            suppressThreadCountSelectionEvent = false;
        }
    }

    private FileTableSortSpec getCurrentFileSortSpec() {

        if (!(fileTable.getRowSorter()
                instanceof FileTableRowSorter sorter)) {

            return FileTableSortSpec.defaultSpec();
        }

        int column =
                sorter.getPrimarySortColumn();

        SortOrder order =
                sorter.getPrimarySortOrder();

        FileTableSortSpec.Column sortColumn;

        switch (column) {

            case FileTableModel.COL_SIZE:

                sortColumn =
                        FileTableSortSpec.Column.SIZE;

                break;

            case FileTableModel.COL_LAST_MODIFIED:

                sortColumn =
                        FileTableSortSpec.Column.LAST_MODIFIED;

                break;

            case FileTableModel.COL_NAME:

            default:

                sortColumn =
                        FileTableSortSpec.Column.NAME;

                break;
        }

        return new FileTableSortSpec(
                sortColumn,
                order != SortOrder.DESCENDING);
    }

    private int getSelectedFileTableRowLimit() {

        if (fileTableRowLimitCombo == null) {
            return 500;
        }

        Integer selected =
                (Integer)
                        fileTableRowLimitCombo
                                .getSelectedItem();

        if (selected == null
                || selected <= 0) {

            return 500;
        }

        return selected;
    }

    private void reloadCurrentFileTable() {

        String bucket =
                currentFileBucket;

        String prefix =
                currentFilePrefix;

        if (bucket == null) {
            return;
        }

        if (prefix == null) {
            prefix =
                    S3TreeNode.ROOT_PREFIX;
        }

        log.debug(
                "[FILE TABLE RELOAD] bucket={} prefix={} limit={}",
                bucket,
                prefix,
                getSelectedFileTableRowLimit());

        loadFiles(
                bucket,
                prefix);
    }

    private void applyLimitedFolderContent(
            String bucket,
            String prefix,
            LimitedFolderContent content) {

        List<S3FileItem> rows =
                new ArrayList<>();

        /*
         * Parent folder.
         *
         * Root'ta ".." göstermiyoruz.
         */
        if (prefix != null
                && !prefix.isEmpty()) {

            rows.add(
                    new S3FileItem(
                            this.getCurrentRepository()
                                    .getName(),
                            bucket,
                            prefix
                                    + S3FileItem
                                    .PARENT_FOLDER_NAME,
                            0,
                            null,
                            true));
        }

        /*
         * -------------------------------------------------
         * TÜM KLASÖRLER
         * -------------------------------------------------
         *
         * Klasör sayısı Max Line Count'tan etkilenmez.
         */
        rows.addAll(
                content.folders()
                        .stream()
                        .map(folder ->
                                new S3FileItem(
                                        this.getCurrentRepository()
                                                .getName(),
                                        bucket,
                                        folder,
                                        0,
                                        null,
                                        true))
                        .toList());

        /*
         * -------------------------------------------------
         * SINIRLI DOSYALAR
         * -------------------------------------------------
         *
         * Burada yalnızca bounded collection'da
         * kalan dosyalar bulunur.
         */
        rows.addAll(
                content.files()
                        .stream()
                        .filter(object ->
                                !object.key()
                                        .endsWith("/"))
                        .map(object ->
                                new S3FileItem(
                                        this.getCurrentRepository()
                                                .getName(),
                                        bucket,
                                        object.key(),
                                        object.size(),
                                        object.lastModified(),
                                        false))
                        .toList());

        /*
         * JTable'a sonucu tek seferde veriyoruz.
         *
         * Lazy loading yok.
         */
        fileTableModel.setFiles(rows);

        /*
         * -------------------------------------------------
         * BİLGİ SATIRI
         * -------------------------------------------------
         */
        long folderCount =
                content.folders().size();

        long fileCount =
                content.files().size();

        String fileText;

        if (content.fileLimitReached()) {

            fileText =
                    content.fileCount()
                            + " / "
                            + content.scannedFileCount()
                            + " file(s)";

        } else {

            fileText =
                    content.fileCount()
                            + " file(s)";
        }

        fileFolderInfo.setText(
                content.folderCount()
                        + " folder(s) and "
                        + fileText);

        log.debug(
                "[FILE TABLE APPLY] bucket={} prefix={} folders={} files={} scannedFiles={} limitReached={}",
                bucket,
                prefix,
                folderCount,
                fileCount,
                content.scannedFileCount(),
                content.fileLimitReached());
    }

    private boolean isFullContentCached(
            String bucket,
            String prefix) {

        return currentFolderFullContent != null
                && Objects.equals(
                currentFolderFullContentBucket,
                bucket)
                && Objects.equals(
                currentFolderFullContentPrefix,
                prefix);
    }

    private void invalidateCurrentFolderFullContentCache() {

        currentFolderFullContent = null;
        currentFolderFullContentBucket = null;
        currentFolderFullContentPrefix = null;
    }

    private void applyCachedFolderContent(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            long generation) {

        if (generation != fileLoadGeneration.get()) {
            return;
        }

        LimitedFolderContent cached =
                currentFolderFullContent;

        if (cached == null) {
            return;
        }

        BoundedSortedFileCollection bounded =
                new BoundedSortedFileCollection(
                        fileLimit,
                        sortSpec.createFileComparator());

        /*
         * Cache'teki TÜM dosyaları yeniden sırala.
         * Burada S3 çağrısı yok.
         */
        bounded.addAll(cached.files());

        boolean fileLimitReached =
                cached.scannedFileCount()
                        > bounded.size();

        LimitedFolderContent displayContent =
                new LimitedFolderContent(
                        cached.folders(),
                        bounded.toList(),
                        fileLimitReached,
                        cached.scannedFileCount());

        applyLimitedFolderContent(
                bucket,
                prefix,
                displayContent);

        setFileTableLoading(false);
    }
}
