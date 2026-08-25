package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.service.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
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
import java.text.CollationKey;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ExplorerPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ExplorerPanel.class);

    private static final int OPERATION_DIALOG_DELAY_MS = 250;
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
    private final AtomicLong operationGeneration = new AtomicLong();
    private final AtomicLong treeLoadGeneration = new AtomicLong();
    private String currentFileBucket;
    private String currentFilePrefix;
    private LimitedFolderContent currentFolderFullContent;
    private String currentFolderFullContentBucket;
    private String currentFolderFullContentPrefix;

    private final Map<String, CollationKey> currentFolderCollationKeyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LimitedFolderContent>> inFlightFileLoads = new java.util.concurrent.ConcurrentHashMap<>();
    
    private JComboBox<Integer> fileTableRowLimitCombo;
    private Consumer<Integer> fileTableRowLimitSelectionListener;
    
    private JComboBox<Integer> threadCountCombo;
    private Consumer<Integer> threadCountSelectionListener;

    private JPanel breadcrumbPanel;
    private JLabel fileFolderInfo;

    private OperationDialog connectionDialog;
    private OperationDialog bucketDialog;
    private OperationDialog fileTableDialog;

    private enum OperationDialogType {
        CONNECTION,
        BUCKET,
        FILE_TABLE
    }
    private final List<OperationDialog> visibleOperationDialogs = new ArrayList<>();
    
    private JPopupMenu filePopup;

    private File lastOpenedFolderToUpload;
    private File lastOpenedFolderToDownload;

    private ExecutorService explorerPool = Executors.newFixedThreadPool(5);
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
    private boolean forceBucketReload;
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
        manageRepositoryAction = new ExplorerAction("Repositories", this::showRepositoryManager);
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
         * Max item count ComboBox
         */
        fileTableRowLimitCombo =
                new JComboBox<>(FILE_TABLE_ROW_LIMITS);

        fileTableRowLimitCombo.setSelectedItem(500);

        fileTableRowLimitCombo.setToolTipText("Max Item Count");
        alignComboBoxRight(fileTableRowLimitCombo);
        
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
        threadCountCombo.setToolTipText("Thread Count");
        alignComboBoxRight(threadCountCombo);
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

            resizeExplorerPool(selected);

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

        UIThemeManager.getThemes()
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

        JSplitPane mainSplit = new JSplitPane(
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
        fileTable.getTableHeader().setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {

            Component c = originalRenderer.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            if (c instanceof JLabel label) {
                label.setIcon(null);

                if (table.getRowSorter() != null) {
                    List<? extends RowSorter.SortKey> sortKeys = table.getRowSorter().getSortKeys();

                    for (RowSorter.SortKey key : sortKeys) {
                        if (key.getColumn() == column && column != 0) {
                            if (key.getSortOrder() == SortOrder.ASCENDING) {
                                label.setIcon(UIManager.getIcon("Table.ascendingSortIcon"));
                            } else if (key.getSortOrder() == SortOrder.DESCENDING) {
                                label.setIcon(UIManager.getIcon("Table.descendingSortIcon"));
                            }
                        }
                    }
                }
            }
            return c;
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

        final long operationId =
                operationGeneration.get();

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

        showOperationDialog(
                OperationDialogType.BUCKET,
                "Loading buckets...");
        hideOperationDialog(
                OperationDialogType.CONNECTION);
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

                    if (S3ErrorResolver.isAccessDenied(ex)
                            && !repository.getExternalBuckets().isEmpty()) {

                        String externalBucket =
                                repository
                                        .getExternalBuckets()
                                        .getFirst();

                        getService().testBucketAccess(
                                externalBucket);

                        log.warn(
                                "[BUCKET LOAD] ListBuckets access denied; " +
                                        "external bucket is accessible: {}",
                                externalBucket);

                        s3Buckets =
                                Collections.emptyList();

                    }
                    else {

                        throw ex;
                    }
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

                    if (operationId !=
                            operationGeneration.get()) {

                        return;
                    }

                    suppressBucketSelectionEvent = true;

                    String selectedBucket;

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
                            selectedBucket)
                            && !forceBucketReload) {

                        log.debug(
                                "[BUCKET LOAD] bucket unchanged={} - tree/table refresh skipped",
                                selectedBucket);

                        hideOperationDialog(
                                OperationDialogType.BUCKET);

                        return;
                    }

                    forceBucketReload = false;

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

                    //hideOperationDialog();
                });

            }
            catch (Exception ex) {

                log.error(
                        "[BUCKET LOAD] failed: {}",
                        S3ErrorResolver.getDetailedMessage(ex),
                        ex);

                SwingUtilities.invokeLater(() -> {
                    if (operationId != operationGeneration.get()) {
                        return;
                    }
                    hideOperationDialog(
                            OperationDialogType.BUCKET);
                    
                    pendingBucketSelection = null;

                    fileTableModel.setFiles(
                            Collections.emptyList());

                    currentFileBucket = null;
                    currentFilePrefix = null;

                    currentFolderFullContent = null;
                    currentFolderFullContentBucket = null;
                    currentFolderFullContentPrefix = null;

                    currentFolderCollationKeyCache.clear();

                    JOptionPane.showMessageDialog(
                            this,
                            S3ErrorResolver.getUserMessage(ex),
                            "Bucket Load Failed",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    public void loadRootFolders(String bucket) {

        final long operationId =
                operationGeneration.get();

        explorerPool.submit(() -> {

            try {

                java.util.List<String> folders =
                        getService().listFolders(
                                bucket,
                                S3TreeNode.ROOT_PREFIX);

                S3TreeNode root =
                        new S3TreeNode(
                                bucket,
                                bucket,
                                S3TreeNode.ROOT_PREFIX);

                nodeCache.clear();

                nodeCache.put(
                        root.getFullPrefix(),
                        root);

                for (String folder : folders) {

                    String displayName =
                            S3Util.extractFolderName(
                                    folder);

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

                    root.add(child);
                }

                SwingUtilities.invokeLater(() -> {

                    /*
                     * Bu repository işlemi artık güncel değilse
                     * UI'ya dokunma.
                     */
                    if (operationId !=
                            operationGeneration.get()) {

                        return;
                    }

                    treeModel.setRoot(root);

                    folderTree.setSelectionRow(0);

                    hideOperationDialog(
                            OperationDialogType.BUCKET);
                    
                    loadFiles(
                            bucket,
                            S3TreeNode.ROOT_PREFIX);

                    updateBreadcrumb(
                            S3TreeNode.ROOT_PREFIX);

                    updateActionStates();
                });

            }
            catch (Exception ex) {

                log.error(
                        "[FOLDER LOAD] failed: {}",
                        S3ErrorResolver.getDetailedMessage(ex),
                        ex);

                SwingUtilities.invokeLater(() -> {

                    /*
                     * Başka bir repository artık aktifse
                     * eski işlemin hatasını gösterme.
                     */
                    if (operationId !=
                            operationGeneration.get()) {

                        return;
                    }

                    hideOperationDialog(
                            OperationDialogType.BUCKET);

                    JOptionPane.showMessageDialog(
                            this,
                            S3ErrorResolver.getUserMessage(ex),
                            "Folder Load Failed",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void loadFiles(
            String bucket,
            String prefix) {

        long generation = fileLoadGeneration.incrementAndGet();

        boolean folderChanged =
                !Objects.equals(
                        currentFileBucket,
                        bucket)
                        || !Objects.equals(
                        currentFilePrefix,
                        prefix);

        if (folderChanged) {
            currentFolderCollationKeyCache.clear();
        }

        currentFileBucket = bucket;
        currentFilePrefix = prefix;

        setFileTableLoading(true);
        showOperationDialog(
                OperationDialogType.FILE_TABLE,
                "<html>"
                        + "<b>Preparing file table...</b><br><br>"
                        + "<b>Bucket:</b> "
                        + bucket
                        + "<br><br>"
                        + "<b>Folder:</b> "
                        + (prefix == null
                        || prefix.isBlank()
                        ? "/"
                        : prefix)
                        + "</html>");
        
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

            if (generation ==
                    fileLoadGeneration.get()) {

                hideOperationDialog(
                        OperationDialogType.FILE_TABLE);
            }
            
            return;
        }

        /*
         * Aynı parametrelerle devam eden bir S3 isteği
         * zaten varsa ikinci bir istek başlatmıyoruz.
         */
        String loadKey =
                createFileLoadKey(
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec);

        CompletableFuture<LimitedFolderContent> future =
                inFlightFileLoads.computeIfAbsent(
                        loadKey,
                        key -> {

                            if (generation ==
                                    fileLoadGeneration.get()) {

                                updateFileDiscoveryProgress(
                                        0,
                                        0);
                            }

                            return CompletableFuture.supplyAsync(
                                    () -> getService()
                                            .listFolderWithLimit(
                                                    bucket,
                                                    prefix,
                                                    fileLimit,
                                                    sortSpec,
                                                    currentFolderCollationKeyCache,
                                                    (fileCount, folderCount) -> {
                                                        if (generation == fileLoadGeneration.get()) {
                                                            updateFileDiscoveryProgress(
                                                                    fileCount,
                                                                    folderCount);
                                                        }
                                                    }),
                                    explorerPool);
                        });

        future.thenAccept(content -> SwingUtilities.invokeLater(() -> {

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

            updateFileFolderInfo(content);

            applyLimitedFolderContent(
                    bucket,
                    prefix,
                    content);

            setFileTableLoading(false);
            hideOperationDialog(
                    OperationDialogType.FILE_TABLE);
        })).exceptionally(ex -> {

            log.error(
                    "Explorer operation failed: {}",
                    S3ErrorResolver.getDetailedMessage(ex),
                    ex);

            SwingUtilities.invokeLater(() -> {

                if (generation != fileLoadGeneration.get()) {
                    return;
                }

                setFileTableLoading(false);
                hideOperationDialog(
                        OperationDialogType.FILE_TABLE);
                JOptionPane.showMessageDialog(
                        this,
                        S3ErrorResolver.getUserMessage(ex),
                        "S3 Operation Failed",
                        JOptionPane.ERROR_MESSAGE);
            });

            return null;
        }).whenComplete(
                (result, throwable) ->
                        inFlightFileLoads.remove(
                                loadKey,
                                future));
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

    private S3ExplorerService getService() {
        RepositoryDefinition repo = context.getActiveRepository();
        if (repo == null) {
            throw new IllegalStateException("No active repository selected");
        }

        return new S3ExplorerService(clientManager.getClient(context.getActiveRepository()));
    }

    public void setSelectedRepository(
            RepositoryDefinition repository) {

        /*
         * Her repository seçimi önceki asenkron
         * işlemleri geçersiz kılar.
         */
        operationGeneration.incrementAndGet();
        treeLoadGeneration.incrementAndGet();
        
        /*
         * Önceki repository'nin UI state'i artık geçerli değil.
         */
        pendingBucketSelection = null;

        currentFileBucket = null;
        currentFilePrefix = null;

        currentFolderFullContent = null;
        currentFolderFullContentBucket = null;
        currentFolderFullContentPrefix = null;

        currentFolderCollationKeyCache.clear();

        nodeCache.clear();

        fileTableModel.setFiles(
                Collections.emptyList());

        bucketCombo.removeAllItems();

        treeModel.setRoot(
                new S3TreeNode(
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX));

        folderTree.clearSelection();

        setFileTableLoading(false);

        hideOperationDialog(
                OperationDialogType.CONNECTION);

        /*
         * Empty Repository seçildiyse yalnızca ekranı
         * temizlemek yeterli.
         */
        if (repository == null
                || repository.isEmpty()) {

            context.setActiveRepository(
                    RepositoryDefinition.EMPTY_REPOSITORY);

            updateActionStates();

            return;
        }

        /*
         * Gerçek repository artık aktif.
         */
        context.setActiveRepository(repository);

        setFileTableLoading(true);

        showOperationDialog(
                OperationDialogType.CONNECTION,
                "Connecting to S3 repository...");

        reloadBuckets();
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

        final long generation =
                treeLoadGeneration.incrementAndGet();
        
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
                if (generation !=
                        treeLoadGeneration.get()) {

                    log.debug(
                            "[TREE APPLY SKIPPED] stale tree load prefix={}",
                            parentNode.getFullPrefix());

                    return;
                }
                
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
            
            SwingUtilities.invokeLater(fileTable::requestFocusInWindow);
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
        this.themeCombo.setSelectedItem(UIThemeManager.getThemeByName(themeName));
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

    private void refreshCurrentTable() {

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        String prefix =
                getCurrentPrefix();

        invalidateFileContentCache(
                bucket,
                prefix);

        fileLoadGeneration.incrementAndGet();

        loadFiles(
                bucket,
                prefix);

        updateActionStates();
    }

    private void invalidateFileContentCache(
            String bucket,
            String prefix) {

        if (bucket == null
                || prefix == null) {

            return;
        }

        if (Objects.equals(
                currentFolderFullContentBucket,
                bucket)
                && Objects.equals(
                currentFolderFullContentPrefix,
                prefix)) {

            log.debug(
                    "[FILE CACHE INVALIDATE] bucket={} prefix={}",
                    bucket,
                    prefix);

            currentFolderFullContent = null;
            currentFolderFullContentBucket = null;
            currentFolderFullContentPrefix = null;
        }
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
        double yiq = (double) ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) / 1000;
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

        lastOpenedFolderToDownload = destination.toFile();
    }

    public void deleteSelected() {
        List<S3FileItem> items = getSelectedItems();
        if (items.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (S3FileItem item : items) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(item.getKey());
        }
        String message;
        if (items.size() == 1) {
            message = "Delete " + sb + " ?";
        }
        else {
            message = "Delete followings?\n" + sb;
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

    private void onRepositoryChanged(
            RepositoryDefinition changedRepository) {

        if (changedRepository == null
                || changedRepository.getName() == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            String changedName =
                    changedRepository.getName();

            int index = -1;

            for (int i = 0;
                 i < repositoryCombo.getItemCount();
                 i++) {

                RepositoryDefinition item =
                        repositoryCombo.getItemAt(i);

                if (item != null
                        && Objects.equals(
                        item.getName(),
                        changedName)) {

                    index = i;
                    break;
                }
            }

            if (index < 0) {
                return;
            }

            RepositoryDefinition activeRepository =
                    context.getActiveRepository();

            boolean wasActive =
                    activeRepository != null
                            && Objects.equals(
                            activeRepository.getName(),
                            changedName);

            repositoryCombo.removeItemAt(index);
            repositoryCombo.insertItemAt(
                    changedRepository,
                    index);

            if (wasActive) {

                context.setActiveRepository(
                        changedRepository);

                repositoryCombo.setSelectedIndex(
                        index);

                reloadBuckets();
            }
        });
    }
    
    public void setThreadCountSelectionListener(
            Consumer<Integer> listener) {

        this.threadCountSelectionListener =
                listener;
    }

    public void selectThreadCount(
            int threadCount) {

        if (threadCount <= 0) {
            return;
        }

        if (threadCountCombo != null) {

            suppressThreadCountSelectionEvent = true;

            try {

                threadCountCombo.setSelectedItem(
                        threadCount);

            } finally {

                suppressThreadCountSelectionEvent = false;
            }
        }

        resizeExplorerPool(
                threadCount);
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

        FileTableSortSpec.Column sortColumn = switch (column) {
            case FileTableModel.COL_SIZE -> FileTableSortSpec.Column.SIZE;
            case FileTableModel.COL_LAST_MODIFIED -> FileTableSortSpec.Column.LAST_MODIFIED;
            default -> FileTableSortSpec.Column.NAME;
        };

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

        updateFileFolderInfo(content);
        
        log.debug(
                "[FILE TABLE APPLY] bucket={} prefix={} folders={} files={} scannedFiles={} limitReached={}",
                bucket,
                prefix,
                content.folderCount(),
                content.fileCount(),
                content.scannedFileCount(),
                content.fileLimitReached());
    }

    private void updateFileFolderInfo(
            LimitedFolderContent content) {

        long folderCount =
                content.folderCount();

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
                folderCount
                        + " folder(s) and "
                        + fileText);
    }

    private boolean isFullContentCached(
            String bucket,
            String prefix) {

        if (currentFolderFullContent == null) {
            return false;
        }

        if (currentFolderFullContentBucket == null
                || currentFolderFullContentPrefix == null) {

            return false;
        }

        /*
         * Cache yalnızca aynı bucket + prefix için
         * kullanılabilir.
         */
        if (!Objects.equals(
                currentFolderFullContentBucket,
                bucket)) {

            return false;
        }

        if (!Objects.equals(
                currentFolderFullContentPrefix,
                prefix)) {

            return false;
        }

        /*
         * Cache'in temsil ettiği içerik gerçekten
         * tamamlanmış olmalı.
         *
         * fileLimitReached=true ise elimizde yalnızca
         * ilk sayfa vardır ve bunu "tam cache" olarak
         * kullanamayız.
         */
        return !currentFolderFullContent.fileLimitReached();
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
                        sortSpec.createFileComparator(
                                currentFolderCollationKeyCache));

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

    private static void alignComboBoxRight(
            JComboBox<?> comboBox) {

        comboBox.setRenderer(
                new DefaultListCellRenderer() {
                    @Override
                    public Component
                    getListCellRendererComponent(
                            JList<?> list,
                            Object value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus) {

                        JLabel label =
                                (JLabel) super
                                        .getListCellRendererComponent(
                                                list,
                                                value,
                                                index,
                                                isSelected,
                                                cellHasFocus);

                        label.setHorizontalAlignment(
                                SwingConstants.RIGHT);

                        return label;
                    }
                });
    }

    private void updateFileDiscoveryProgress(
            long fileCount,
            long folderCount) {

        SwingUtilities.invokeLater(() ->
                fileFolderInfo.setText(
                        "Preparing... "
                                + folderCount
                                + " folders / "
                                + fileCount
                                + " files discovered"));
    }

    private String createFileLoadKey(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        return bucket
                + "\u0000"
                + prefix
                + "\u0000"
                + fileLimit
                + "\u0000"
                + sortSpec.getColumn()
                + "\u0000"
                + sortSpec.isAscending();
    }

    private synchronized void resizeExplorerPool(
            int threadCount) {

        if (threadCount <= 0) {
            return;
        }

        ExecutorService oldPool =
                explorerPool;

        explorerPool =
                Executors.newFixedThreadPool(
                        threadCount);

        if (oldPool != null) {
            oldPool.shutdown();
        }

        log.info(
                "[EXPLORER POOL] resized to {} threads",
                threadCount);
    }

    public void shutdown() {

        synchronized (this) {

            if (explorerPool != null) {

                explorerPool.shutdown();

                explorerPool = null;
            }
        }
    }

    private OperationDialog createOperationDialog(
            String title) {

        Window owner =
                SwingUtilities.getWindowAncestor(this);

        JDialog dialog =
                new JDialog(
                        owner,
                        title,
                        Dialog.ModalityType.MODELESS);

        dialog.setDefaultCloseOperation(
                WindowConstants.HIDE_ON_CLOSE);

        dialog.setResizable(false);

        JPanel panel =
                new JPanel(
                        new BorderLayout(15, 15));

        panel.setBorder(
                new EmptyBorder(
                        18,
                        20,
                        18,
                        20));

        JLabel message =
                new JLabel("Preparing...");

        panel.add(
                message,
                BorderLayout.CENTER);

        JButton hideButton =
                new JButton("Hide");

        hideButton.addActionListener(
                e -> dialog.setVisible(false));

        JPanel buttonPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                0,
                                0));

        buttonPanel.add(hideButton);

        panel.add(
                buttonPanel,
                BorderLayout.SOUTH);

        dialog.setContentPane(panel);

        dialog.setMinimumSize(
                new Dimension(
                        450,
                        150));

        return new OperationDialog(
                dialog,
                message);
    }

    private static final class OperationDialog {

        private final JDialog dialog;
        private final JLabel message;

        private Timer showTimer;

        private OperationDialog(
                JDialog dialog,
                JLabel message) {

            this.dialog = dialog;
            this.message = message;
        }
    }

    private void showOperationDialog(
            OperationDialogType type,
            String message) {

        SwingUtilities.invokeLater(() -> {

            OperationDialog operationDialog;

            switch (type) {

                case CONNECTION:

                    if (connectionDialog == null) {
                        connectionDialog =
                                createOperationDialog(
                                        "S3 Connection");
                    }

                    operationDialog =
                            connectionDialog;

                    break;

                case BUCKET:

                    if (bucketDialog == null) {
                        bucketDialog =
                                createOperationDialog(
                                        "Bucket Loading");
                    }

                    operationDialog =
                            bucketDialog;

                    break;

                case FILE_TABLE:

                    if (fileTableDialog == null) {
                        fileTableDialog =
                                createOperationDialog(
                                        "File Table");
                    }

                    operationDialog =
                            fileTableDialog;

                    break;

                default:
                    return;
            }

            operationDialog.message.setText(
                    message);

            operationDialog.dialog.pack();

            if (operationDialog.showTimer != null) {
                operationDialog.showTimer.stop();
            }

            if (!visibleOperationDialogs.contains(
                    operationDialog)) {

                visibleOperationDialogs.add(
                        operationDialog);
            }

            operationDialog.dialog.setVisible(false);

            operationDialog.showTimer =
                    new Timer(
                            OPERATION_DIALOG_DELAY_MS,
                            e -> {

                                if (!visibleOperationDialogs.contains(
                                        operationDialog)) {

                                    ((Timer) e.getSource()).stop();

                                    return;
                                }

                                operationDialog.dialog.setVisible(true);

                                positionOperationDialogs();

                                ((Timer) e.getSource()).stop();
                            });

            operationDialog.showTimer.setRepeats(false);

            operationDialog.showTimer.start();
        });
    }

    private void positionOperationDialogs() {

        Window owner =
                SwingUtilities.getWindowAncestor(this);

        if (owner == null) {
            return;
        }

        int centerX =
                owner.getX()
                        + (owner.getWidth() / 2);

        int currentY =
                owner.getY()
                        + (owner.getHeight() * 30 / 100);

        int gap = 12;

        for (OperationDialog operationDialog :
                visibleOperationDialogs) {

            if (operationDialog == null
                    || !operationDialog.dialog.isVisible()) {

                continue;
            }

            int x =
                    centerX
                            - operationDialog.dialog
                            .getWidth() / 2;

            operationDialog.dialog.setLocation(
                    x,
                    currentY);

            currentY +=
                    operationDialog.dialog.getHeight()
                            + gap;
        }
    }

    private void hideOperationDialog(
            OperationDialogType type) {

        SwingUtilities.invokeLater(() -> {
            OperationDialog operationDialog = null;
            
            switch (type) {

                case CONNECTION:

                    if (connectionDialog != null) {
                        operationDialog = connectionDialog;
                    }

                    break;

                case BUCKET:

                    if (bucketDialog != null) {
                        operationDialog = bucketDialog;
                    }

                    break;

                case FILE_TABLE:

                    if (fileTableDialog != null) {
                        operationDialog = fileTableDialog;
                    }

                    break;

                default:
                    return;
            }

            if (operationDialog != null) {

                if (operationDialog.showTimer != null) {

                    operationDialog.showTimer.stop();

                    operationDialog.showTimer = null;
                }

                operationDialog.dialog.setVisible(false);

                visibleOperationDialogs.remove(
                        operationDialog);
            }
            
            positionOperationDialogs();
        });
    }
}
