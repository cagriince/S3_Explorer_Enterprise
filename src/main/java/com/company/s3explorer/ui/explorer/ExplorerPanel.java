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
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ExplorerPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ExplorerPanel.class);

    private static final int OPERATION_DIALOG_DELAY_MS = 250;
    private static final Integer[] FILE_TABLE_ROW_LIMITS = {
            100, 250, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 10000000
    };
    private static final Integer[] THREAD_COUNTS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100
    };

    private ExplorerView view;

    private ExplorerRefreshScheduler refreshScheduler;
    private final ExplorerContentLoader contentLoader;
    private ExplorerTreeController treeController;
    
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
    private String currentFileBucket;
    private String currentFilePrefix;

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

        this.themeManager =
                new UIThemeManager(
                        this,
                        transferPanel);

        this.contentLoader =
                new ExplorerContentLoader(
                        this::getService);

        initialize();
    }

    private void initialize() {

        createActions();

        view = new ExplorerView(
                downloadAction,
                deleteAction,
                copyAction,
                cutAction,
                pasteAction,
                uploadAction,
                newFolderAction,
                refreshAction,
                manageRepositoryAction,
                node -> treeController.loadChildren(node),
                this::openSelectedFileItem,
                this::reloadCurrentFileTable,
                this::updateActionStates,
                clipboard::isEmpty,
                this::resizeExplorerPool,
                this::getCurrentFileSortSpec);

        setLayout(
                new BorderLayout());

        add(
                createMainSplit(),
                BorderLayout.CENTER);

        /*
         * createMainSplit() içinde
         * treeController artık oluşturulmuş durumda.
         */
        refreshScheduler =
                new ExplorerRefreshScheduler(
                        treeController::refreshNode,
                        this::refreshCurrentTable);

        bindEvents();

        defineShortCuts();

        fileTableRowLimitSelectionListener =
                selectedLimit -> {

                    log.debug(
                            "[FILE TABLE LIMIT CHANGED] limit={}",
                            selectedLimit);

                    reloadCurrentFileTable();
                };

        view.setFileTableRowLimitSelectionListener(
                fileTableRowLimitSelectionListener);

        reloadRepositories();

        repositoryManager.addRepositoryChangeListener(
                this::onRepositoryChanged);

        eventBus.subscribe(
                this::onTransferEvent);

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

                        log.info(
                                "[EXPLORER LISTENER ENTERED] event={}",
                                event);

                        ExplorerPanel.this
                                .onTransferGroupCompleted(
                                        event);
                    }
                });
    }

    public void setFolderTreeLeafIcon() {
        view.setFolderTreeLeafIcon();
    }

    public void setButtonIcons() {
        view.setButtonIcons();
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

    private JSplitPane createMainSplit() {

        JSplitPane mainSplit =
                view.createMainSplit();

        folderTree =
                view.getFolderTree();

        treeModel =
                view.getTreeModel();

        fileTable =
                view.getFileTable();

        fileTableModel =
                view.getFileTableModel();

        repositoryCombo =
                view.getRepositoryCombo();

        bucketCombo =
                view.getBucketCombo();

        themeCombo =
                view.getThemeCombo();

        fileTableRowLimitCombo =
                view.getFileTableRowLimitCombo();

        threadCountCombo =
                view.getThreadCountCombo();

        breadcrumbPanel =
                view.getBreadcrumbPanel();

        fileFolderInfo =
                view.getFileFolderInfo();

        treeController =
                new ExplorerTreeController(
                        folderTree,
                        treeModel,
                        contentLoader,
                        () -> explorerPool,
                        this::getCurrentBucket);

        return mainSplit;
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

                    contentLoader.clearCollationKeyCache();

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

        final String prefix =
                S3TreeNode.ROOT_PREFIX;

        final int fileLimit =
                getSelectedFileTableRowLimit();

        final FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        contentLoader.loadFolder(
                        explorerPool,
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec)
                .thenAccept(content ->
                        SwingUtilities.invokeLater(() -> {

                            /*
                             * Bu repository işlemi artık güncel değilse
                             * UI'ya dokunma.
                             */
                            if (operationId !=
                                    operationGeneration.get()) {

                                return;
                            }

                            /*
                             * Tek S3 listing sonucundan Folder Tree'yi kur.
                             */
                            treeController.applyRootFolders(
                                    bucket,
                                    content.folders());

                            hideOperationDialog(
                                    OperationDialogType.BUCKET);

                            /*
                             * IMPORTANT:
                             *
                             * content was already obtained above.
                             * Do NOT call loadFiles(bucket, prefix),
                             * because that would create another
                             * content-loading request.
                             *
                             * Apply the existing result directly.
                             */
                            loadFiles(
                                    bucket,
                                    prefix,
                                    content);

                            updateBreadcrumb(
                                    prefix);

                            updateActionStates();
                        }))
                .exceptionally(ex -> {

                    log.error(
                            "[FOLDER LOAD] failed: {}",
                            S3ErrorResolver
                                    .getDetailedMessage(ex));

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
                                S3ErrorResolver
                                        .getUserMessage(ex),
                                "Folder Load Failed",
                                JOptionPane.ERROR_MESSAGE);
                    });

                    return null;
                });
    }
    
    private void loadFiles(
            String bucket,
            String prefix) {

        final long generation =
                fileLoadGeneration.incrementAndGet();

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
                        + (prefix == null || prefix.isBlank()
                        ? "/"
                        : prefix)
                        + "</html>");

        final int fileLimit =
                getSelectedFileTableRowLimit();

        final FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        updateFileDiscoveryProgress(
                0,
                0);

        contentLoader.loadFolder(
                        explorerPool,
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec,
                        (fileCount, folderCount) -> {

                            if (generation ==
                                    fileLoadGeneration.get()) {

                                updateFileDiscoveryProgress(
                                        fileCount,
                                        folderCount);
                            }
                        })
                .thenAccept(content ->
                        SwingUtilities.invokeLater(() -> {

                            if (generation !=
                                    fileLoadGeneration.get()) {
                                return;
                            }

                            updateFileFolderInfo(
                                    content);

                            applyLimitedFolderContent(
                                    bucket,
                                    prefix,
                                    content);

                            setFileTableLoading(false);

                            hideOperationDialog(
                                    OperationDialogType.FILE_TABLE);
                        }))
                .exceptionally(ex -> {

                    log.error(
                            "[FILE LOAD] failed: {}",
                            S3ErrorResolver
                                    .getDetailedMessage(ex));

                    SwingUtilities.invokeLater(() -> {

                        if (generation !=
                                fileLoadGeneration.get()) {
                            return;
                        }

                        setFileTableLoading(false);

                        hideOperationDialog(
                                OperationDialogType.FILE_TABLE);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver
                                        .getUserMessage(ex),
                                "S3 Operation Failed",
                                JOptionPane.ERROR_MESSAGE);
                    });

                    return null;
                });
    }

    private void loadFiles(
            String bucket,
            String prefix,
            LimitedFolderContent content) {

        final long generation =
                fileLoadGeneration.incrementAndGet();

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
                        + (prefix == null || prefix.isBlank()
                        ? "/"
                        : prefix)
                        + "</html>");

        SwingUtilities.invokeLater(() -> {

            if (generation !=
                    fileLoadGeneration.get()) {
                return;
            }

            updateFileFolderInfo(
                    content);

            applyLimitedFolderContent(
                    bucket,
                    prefix,
                    content);

            setFileTableLoading(false);

            hideOperationDialog(
                    OperationDialogType.FILE_TABLE);
        });
    }

    private void bindEvents() {

        themeCombo.addActionListener(e -> {

            UITheme theme =
                    (UITheme) themeCombo.getSelectedItem();

            themeManager.changeTheme(theme);

            if (themeSelectionListener != null) {

                themeSelectionListener.accept(theme);
            }
        });

        repositoryCombo.addActionListener(e -> {

            RepositoryDefinition repository =
                    this.getCurrentRepository();

            if (repository == null) {
                return;
            }

            if (repositorySelectionListener != null) {

                repositorySelectionListener.accept(
                        repository);
            }

            setSelectedRepository(repository);
        });

        bucketCombo.addActionListener(e -> {

            if (suppressBucketSelectionEvent) {
                return;
            }

            String bucket =
                    this.getCurrentBucket();

            if (bucket == null) {
                return;
            }

            if (bucketSelectionListener != null) {

                bucketSelectionListener.accept(
                        bucket);
            }

            /*
             * Bucket değiştiğinde:
             *
             *     Tree root
             *     +
             *     File Table root
             *
             * aynı S3 listing sonucundan yüklenir.
             */
            loadRootFolders(bucket);
        });

        /*
         * ---------------------------------------------------------
         * TREE SELECTION
         * ---------------------------------------------------------
         *
         * Selection artık Tree children yüklemez.
         *
         * Tree children yalnızca ExplorerTreeController
         * tarafından Tree EXPAND olayında yüklenir.
         *
         * Burada yalnızca seçilen klasörün File Table'ı
         * yüklenir.
         */
        folderTree.addTreeSelectionListener(e -> {

            String bucket =
                    this.getCurrentBucket();

            if (bucket == null) {
                return;
            }

            TreePath selectedPath =
                    e.getNewLeadSelectionPath();

            if (selectedPath == null) {
                return;
            }

            Object selectedObject =
                    selectedPath.getLastPathComponent();

            if (!(selectedObject instanceof S3TreeNode selectedNode)) {
                return;
            }

            String prefix =
                    selectedNode.getFullPrefix();

            log.info(
                    "[TREE SELECTION] bucket={} prefix={} node={}",
                    bucket,
                    prefix,
                    selectedNode);

            /*
             * Tree selection event'i tamamen bitsin.
             *
             * Özellikle programatik selection sırasında
             * setSelectionPath() henüz tamamlanmadan
             * File Table işlemlerine girmiyoruz.
             */
            SwingUtilities.invokeLater(() -> {

                /*
                 * Selection hâlâ aynı mı?
                 */
                TreePath actualPath =
                        folderTree.getSelectionPath();

                if (actualPath == null
                        || !actualPath.equals(selectedPath)) {

                    log.warn(
                            "[TREE SELECTION] selection changed before processing prefix={}",
                            prefix);

                    return;
                }

                loadFiles(
                        bucket,
                        prefix);

                updateBreadcrumb(
                        prefix);

                updateActionStates();

                SwingUtilities.invokeLater(() -> {

                    fileTable.requestFocusInWindow();

                    log.info(
                            "[FILE TABLE FOCUS] requested after tree selection prefix={}",
                            prefix);
                });
            });
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

        pendingBucketSelection = null;

        currentFileBucket = null;
        currentFilePrefix = null;

        contentLoader.clearCollationKeyCache();

        treeController.clearState();

        fileTableModel.setFiles(
                Collections.emptyList());

        bucketCombo.removeAllItems();

        treeController.initializeRoot();

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

        String previousBucket =
                getCurrentBucket();

        pendingBucketSelection =
                previousBucket;

        bucketCombo.removeAllItems();

        treeController.initializeRoot();

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

    private S3FileItem getSelectedFileItem() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }

        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return fileTableModel.getItem(modelRow);
    }

    private void openSelectedFileItem() {

        log.info(
                "[FILE TABLE OPEN] invoked selectedRow={} rowCount={}",
                fileTable.getSelectedRow(),
                fileTable.getSelectedRowCount());

        S3FileItem item =
                getSelectedFileItem();

        if (item == null) {
            log.warn(
                    "[FILE TABLE OPEN] selected item is NULL");
            return;
        }

        log.info(
                "[FILE TABLE OPEN] item name={} key={} folder={} parent={}",
                item.getName(),
                item.getKey(),
                item.isFolder(),
                item.isParentFolder());

        if (item.isFolder()) {
            navigateToFolder(item);

            SwingUtilities.invokeLater(
                    fileTable::requestFocusInWindow);

            return;
        }

        downloadSelected();
    }

    private void navigateToFolder(S3FileItem item) {

        if (item == null) {
            return;
        }

        String bucket = getCurrentBucket();

        if (bucket == null) {
            return;
        }

        String targetPrefix;

        if (item.isParentFolder()) {

            targetPrefix =
                    S3Util.extractParentPrefix(
                            currentFilePrefix);

        } else {

            targetPrefix =
                    item.getKey();
        }

        if (targetPrefix == null) {
            return;
        }

        log.info(
                "[FILE TABLE NAVIGATION] bucket={} targetPrefix={}",
                bucket,
                targetPrefix);

        treeController.selectPrefix(targetPrefix);
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

    private void refreshCurrentTable() {

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        String prefix =
                getCurrentPrefix();

        contentLoader.invalidate(
                bucket,
                prefix);

        fileLoadGeneration.incrementAndGet();

        loadFiles(
                bucket,
                prefix);

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
        double yiq = (double) ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) / 1000;
        return (yiq >= 128) ? Color.BLACK : Color.WHITE;
    }

    private void navigateToPrefix(
            String prefix) {

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        treeController.selectPrefix(prefix);

        loadFiles(
                bucket,
                prefix);
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
        return treeController.getSelectedNode();
    }

    private String getCurrentPrefix() {
        return treeController.getSelectedPrefix();
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

        log.info("[PARENT NAV] ENTER");

        String currentPrefix =
                currentFilePrefix;

        log.info(
                "[PARENT NAV] currentFilePrefix={}",
                currentPrefix);

        if (currentPrefix == null
                || currentPrefix.isBlank()) {

            log.warn(
                    "[PARENT NAV] currentFilePrefix is NULL/BLANK");

            return;
        }

        String parentPrefix =
                S3Util.extractParentPrefix(
                        currentPrefix);

        log.info(
                "[PARENT NAV] parentPrefix={}",
                parentPrefix);

        if (parentPrefix == null) {
            return;
        }

        log.info(
                "[PARENT NAV] navigating to parent={}",
                parentPrefix);

        treeController.selectPrefix(
                parentPrefix);

        SwingUtilities.invokeLater(() ->
                fileTable.requestFocusInWindow());
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
        view.setThreadCountSelectionListener(listener);
    }

    public void selectThreadCount(
            int threadCount) {
        view.selectThreadCount(threadCount);
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
