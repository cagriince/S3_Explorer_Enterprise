package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.application.ActiveRepositoryContext;
import com.company.s3explorer.repository.RepositoryChangeEvent;
import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.repository.RepositoryManager;
import com.company.s3explorer.security.EncryptionConfig;
import com.company.s3explorer.service.*;
import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
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
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ExplorerPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ExplorerPanel.class);

    private static final int OPERATION_DIALOG_DELAY_MS = 250;

    private ExplorerView view;

    private EncryptionConfig encryptionConfig;
    private ExplorerRefreshScheduler refreshScheduler;
    private final ExplorerContentLoader contentLoader;
    private ExplorerTreeController treeController;
    private ExplorerFileOperationController fileOperationController;
    private ExplorerClipboardController clipboardController;
    private ExplorerPasteController pasteController;
    private ExplorerRenameController renameController;
    private ExplorerDeleteController deleteController;

    private final AtomicLong fileLoadGeneration = new AtomicLong();
    private final AtomicLong operationGeneration = new AtomicLong();
    private String currentFileBucket;
    private String currentFilePrefix;

    private OperationDialog connectionDialog;
    private OperationDialog bucketDialog;
    private OperationDialog fileTableDialog;

    private enum OperationDialogType {
        CONNECTION,
        BUCKET,
        FILE_TABLE
    }
    private final List<OperationDialog> visibleOperationDialogs = new ArrayList<>();

    private File lastOpenedFolderToUpload;
    private File lastOpenedFolderToDownload;

    private int pendingDeleteSelectionViewRow = -1;
    private final Set<String> pendingFolderDeleteKeys = ConcurrentHashMap.newKeySet();
    
    private String pendingFileTableRenameOldKey;
    private String pendingFileTableSelectionKey;
    private List<String> pendingFileTableSelectionKeys;
    private boolean restoreFileTableFocus;
    private boolean pasteSelectionCollectionInProgress;
    private List<String> preservedFileTableSelectionKeys;
    private boolean forceFileTableFocusAfterRefresh;
    
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
    private boolean suppressRepositorySelectionEvent;
    private boolean forceBucketReload;

    private final Map<UUID, List<TransferTask>> completedGroupTasks = new ConcurrentHashMap<>();
    
    private final ExplorerClipboard clipboard = new ExplorerClipboard();

    private Action downloadAction;
    private Action downloadDecryptedAction;
    private Action deleteAction;
    private Action copyAction;
    private Action cutAction;
    private Action pasteAction;
    private Action copyTextAction;
    private Action uploadAction;
    private Action uploadEncryptedAction;
    private Action newFolderAction;
    private Action refreshAction;
    private Action manageRepositoryAction;
    private Action goToParentAction;
    private Action renameAction;
    private Action propertiesAction;
    private Action bulkDownloadAction;

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

        clipboardController =
                new ExplorerClipboardController(
                        clipboard);

        view = new ExplorerView(
                downloadAction,
                downloadDecryptedAction,
                bulkDownloadAction,
                deleteAction,
                copyAction,
                renameAction,
                propertiesAction,
                cutAction,
                pasteAction,
                copyTextAction,
                uploadAction,
                uploadEncryptedAction,
                newFolderAction,
                refreshAction,
                manageRepositoryAction,
                node -> treeController.loadChildren(node),
                this::openSelectedFileItem,
                this::reloadCurrentFileTable,
                this::updateActionStates,
                clipboardController::isEmpty,
                this::hasEncryptionConfiguration,
                this::resizeExplorerPool);

        setLayout(
                new BorderLayout());

        add(
                createMainSplit(),
                BorderLayout.CENTER);

        pasteController =
                new ExplorerPasteController(
                        clipboard,
                        transferManager,
                        fileOperationController,
                        this::getCurrentRepository,
                        this::getCurrentBucket,
                        this::getCurrentPrefix,
                        this::exists,
                        this::confirmFileConflict,
                        value ->
                                pasteSelectionCollectionInProgress = value,
                        keys ->
                                pendingFileTableSelectionKeys = keys,
                        value ->
                                restoreFileTableFocus = value,
                        value ->
                                forceFileTableFocusAfterRefresh = value,
                        this::updateActionStates);

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

        Consumer<Integer> fileTableRowLimitSelectionListener =
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
        uploadEncryptedAction = new ExplorerAction("Upload Encrypted", this::uploadFileEncrypted);
        newFolderAction = new ExplorerAction("New Folder", this::createFolder);
        downloadAction = new ExplorerAction("Download", this::downloadSelected);
        downloadDecryptedAction = new ExplorerAction("Download Decrypted", this::downloadSelectedDecrypted);
        deleteAction = new ExplorerAction("Delete", () -> deleteController.deleteSelectedWithFocusRestore());
        copyAction = new ExplorerAction("Copy", this::copySelected);
        cutAction = new ExplorerAction("Cut", this::moveSelected);
        pasteAction = new ExplorerAction("Paste", this::pasteClipboard);
        copyTextAction = new ExplorerAction("Copy Text", this::copyText);
        goToParentAction = new ExplorerAction("GoToParent", this::goToParentFolder);
        renameAction = new ExplorerAction("Rename", () -> renameController.renameSelected());
        propertiesAction = new ExplorerAction("Properties", this::showProperties);
        bulkDownloadAction = new ExplorerAction("Bulk Download", this::showBulkDownloadDialog);
    }

    private void defineShortCuts() {
        // -------------------------------------------------
        // File Table
        // -------------------------------------------------

        InputMap inputMap = view.getFileTable().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = view.getFileTable().getActionMap();

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

        // Delete
        inputMap.put(KeyStroke.getKeyStroke("DELETE"), "deleteSelected");
        actionMap.put("deleteSelected", deleteAction);

        // Rename
        inputMap.put(KeyStroke.getKeyStroke("F2"),"renameSelected");
        actionMap.put("renameSelected", renameAction);

        // Properties
        inputMap.put(KeyStroke.getKeyStroke("alt ENTER"), "showProperties");
        actionMap.put("showProperties", propertiesAction);
        
        // -------------------------------------------------
        // Tree
        // -------------------------------------------------

        InputMap treeInputMap = view.getFolderTree().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap treeActionMap = view.getFolderTree().getActionMap();
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

        treeController =
                new ExplorerTreeController(
                        view.getFolderTree(),
                        view.getTreeModel(),
                        contentLoader,
                        () -> explorerPool,
                        this::getCurrentBucket);

        fileOperationController =
                new ExplorerFileOperationController(
                        transferManager,
                        () -> {
                            RepositoryDefinition repository =
                                    getCurrentRepository();

                            return repository == null
                                    ? null
                                    : repository.getId();
                        },
                        this::getCurrentBucket);

        renameController =
                new ExplorerRenameController(
                        view,
                        transferManager,
                        this::getCurrentBucket,
                        this::exists,
                        this::updateActionStates,
                        value ->
                                pendingFileTableRenameOldKey = value,
                        value ->
                                pendingFileTableSelectionKey = value,
                        value ->
                                restoreFileTableFocus = value);

        deleteController =
                new ExplorerDeleteController(
                        view,
                        transferManager,
                        fileOperationController,
                        this::getCurrentBucket,
                        value ->
                                restoreFileTableFocus = value,
                        value ->
                                pendingDeleteSelectionViewRow = value,
                        pendingFolderDeleteKeys,
                        this::updateActionStates);

        return mainSplit;
    }

    public void updateActionStates() {

        boolean folderSelected =
                currentFileBucket != null
                        && currentFilePrefix != null;

        JTable table =
                view.getFileTable();

        int selectedRowCount =
                table.getSelectedRowCount();

        boolean hasSelection = false;

        if (folderSelected
                && selectedRowCount > 0) {

            if (selectedRowCount > 1) {

                hasSelection = true;

            } else {

                int viewRow =
                        table.getSelectedRow();

                if (viewRow >= 0) {

                    int modelRow =
                            table.convertRowIndexToModel(
                                    viewRow);

                    S3FileItem item =
                            view.getFileTableModel()
                                    .getItem(modelRow);

                    hasSelection =
                            item != null
                                    && !item.isParentFolder();
                }
            }
        }

        boolean hasClipboard =
                folderSelected
                        && !clipboard.isEmpty();

        newFolderAction.setEnabled(
                folderSelected);

        uploadAction.setEnabled(
                folderSelected);

        uploadEncryptedAction.setEnabled(
                folderSelected
                        && hasEncryptionConfiguration());

        downloadAction.setEnabled(
                hasSelection);

        downloadDecryptedAction.setEnabled(
                hasSelection
                        && hasEncryptionConfiguration());

        deleteAction.setEnabled(
                hasSelection);

        renameAction.setEnabled(
                hasSelection);

        propertiesAction.setEnabled(
                view.getFileTable().getSelectedRowCount() == 1
                        && getSelectedFileItem() != null
                        && !getSelectedFileItem().isParentFolder());
        
        copyAction.setEnabled(
                hasSelection);

        cutAction.setEnabled(
                hasSelection);

        pasteAction.setEnabled(
                hasClipboard);

        copyTextAction.setEnabled(
                hasSelection);
        
        log.debug(
                "[ACTION STATES] folderSelected={} selectedRows={} hasSelection={} hasClipboard={}",
                folderSelected,
                selectedRowCount,
                hasSelection,
                hasClipboard);
    }

    private void deleteObject(S3FileItem item) {

        try {

            fileOperationController.delete(item);

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
                    view.getRepositoryCombo().removeAllItems();
                    view.getRepositoryCombo().addItem(RepositoryDefinition.EMPTY_REPOSITORY);
                    Collator turkishCollator =
                            Collator.getInstance(
                                    new Locale("tr", "TR"));
                    turkishCollator.setStrength(
                            Collator.PRIMARY);

                    repositories.sort(
                            (first, second) ->
                                    S3Util.naturalTurkishCompare(
                                            first.getId(),
                                            second.getId(),
                                            turkishCollator
                                    )
                    );

                    repositories.forEach(
                            view.getRepositoryCombo()::addItem);

                    if (pendingRepositorySelection != null) {
                        view.getRepositoryCombo().setSelectedItem(pendingRepositorySelection);
                        pendingRepositorySelection = null;
                    }
                    else {
                        view.getRepositoryCombo().setSelectedItem(RepositoryDefinition.EMPTY_REPOSITORY);
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
                        view.getRepositoryCombo().getSelectedItem();

        if (selectedRepository == null
                || selectedRepository ==
                RepositoryDefinition.EMPTY_REPOSITORY) {

            log.debug(
                    "[BUCKET LOAD] no repository selected");

            return;
        }

        /*
         * Repository selection değişmişse
         * bu işlem artık geçerli değildir.
         */
        RepositoryDefinition activeRepository =
                context.getActiveRepository();

        if (activeRepository == null
                || activeRepository.isEmpty()
                || !Objects.equals(
                activeRepository.getId(),
                selectedRepository.getId())) {

            log.debug(
                    "[BUCKET LOAD] repository is no longer active: {}",
                    selectedRepository.getId());

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
                 * Worker thread başlamış olsa bile repository
                 * artık değişmiş olabilir.
                 *
                 * Özellikle EMPTY repository seçildiyse burada
                 * S3 bağlantısına kesinlikle girme.
                 */
                if (operationId !=
                        operationGeneration.get()) {

                    log.debug(
                            "[BUCKET LOAD] cancelled before execution " +
                                    "repository={}",
                            selectedRepository.getId());

                    return;
                }

                RepositoryDefinition currentRepository =
                        context.getActiveRepository();

                if (currentRepository == null
                        || currentRepository.isEmpty()
                        || !Objects.equals(
                        currentRepository.getId(),
                        selectedRepository.getId())) {

                    log.debug(
                            "[BUCKET LOAD] active repository changed before S3 call " +
                                    "selected={} active={}",
                            selectedRepository.getId(),
                            currentRepository == null
                                    ? null
                                    : currentRepository.getId());

                    return;
                }

                /*
                 * RepositoryManager'dan güncel repository
                 * tanımını al.
                 */
                RepositoryDefinition repository =
                        repositoryManager.findById(
                                selectedRepository.getId());

                if (repository == null
                        || repository ==
                        RepositoryDefinition.EMPTY_REPOSITORY) {

                    log.warn(
                            "[BUCKET LOAD] repository not found: {}",
                            selectedRepository.getId());

                    return;
                }

                /*
                 * Repository değişmiş olabilir.
                 * findById() sonrasında tekrar doğrula.
                 */
                if (operationId !=
                        operationGeneration.get()) {

                    log.debug(
                            "[BUCKET LOAD] cancelled after repository lookup " +
                                    "repository={}",
                            selectedRepository.getId());

                    return;
                }

                currentRepository =
                        context.getActiveRepository();

                if (currentRepository == null
                        || currentRepository.isEmpty()
                        || !Objects.equals(
                        currentRepository.getId(),
                        repository.getId())) {

                    log.debug(
                            "[BUCKET LOAD] active repository changed before S3 call " +
                                    "repository={}",
                            repository.getId());

                    return;
                }

                log.debug(
                        "[BUCKET LOAD START] repository={} previousBucket={} externalBuckets={}",
                        repository.getId(),
                        previousBucket,
                        repository.getExternalBuckets());

                /*
                 * Gerçek S3 bucket'larını al.
                 */
                List<String> s3Buckets;

                try {

                    /*
                     * Son kontrol:
                     * getService().listBuckets() çağrısından hemen önce
                     * repository hâlâ geçerli mi?
                     */
                    if (operationId !=
                            operationGeneration.get()) {

                        log.debug(
                                "[BUCKET LOAD] cancelled before listBuckets " +
                                        "repository={}",
                                repository.getId());

                        return;
                    }

                    s3Buckets =
                            getService().listBuckets();

                } catch (Exception ex) {

                    if (S3ErrorResolver.isAccessDenied(ex)
                            && repository.hasExternalBucket()) {

                        /*
                         * Empty repository seçimi sırasında
                         * eski işlem burada da durdurulmalı.
                         */
                        if (operationId !=
                                operationGeneration.get()) {

                            log.debug(
                                    "[BUCKET LOAD] cancelled before external bucket access " +
                                            "repository={}",
                                    repository.getId());

                            return;
                        }

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

                    } else {

                        throw ex;
                    }
                }

                /*
                 * S3 çağrısı tamamlandıktan sonra da işlem
                 * hâlâ güncel mi kontrol et.
                 */
                if (operationId !=
                        operationGeneration.get()) {

                    log.debug(
                            "[BUCKET LOAD] cancelled after S3 call " +
                                    "repository={}",
                            repository.getId());

                    return;
                }

                RepositoryDefinition finalActiveRepository =
                        context.getActiveRepository();

                if (finalActiveRepository == null
                        || finalActiveRepository.isEmpty()
                        || !Objects.equals(
                        finalActiveRepository.getId(),
                        repository.getId())) {

                    log.debug(
                            "[BUCKET LOAD] active repository changed after S3 call " +
                                    "repository={}",
                            repository.getId());

                    return;
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

                    RepositoryDefinition active =
                            context.getActiveRepository();

                    if (active == null
                            || active.isEmpty()
                            || !Objects.equals(
                            active.getId(),
                            repository.getId())) {

                        return;
                    }

                    suppressBucketSelectionEvent = true;

                    String selectedBucket;

                    try {

                        /*
                         * ComboBox'ı yeniden doldur.
                         */
                        view.getBucketCombo().removeAllItems();

                        List<String> sortedBuckets =
                                new ArrayList<>(allBuckets);

                        Collator turkishCollator =
                                Collator.getInstance(
                                        new Locale("tr", "TR"));

                        turkishCollator.setStrength(
                                Collator.PRIMARY);

                        sortedBuckets.sort(
                                (first, second) ->
                                        turkishCollator.compare(
                                                first,
                                                second));

                        for (String bucket :
                                sortedBuckets) {

                            view.getBucketCombo().addItem(bucket);
                        }

                        /*
                         * Önce mevcut bucket'ı korumaya çalış.
                         */
                        if (previousBucket != null
                                && allBuckets.contains(
                                previousBucket)) {

                            view.getBucketCombo().setSelectedItem(
                                    previousBucket);

                        } else if (
                                view.getBucketCombo().getItemCount() > 0) {

                            view.getBucketCombo().setSelectedIndex(0);
                        }

                        selectedBucket =
                                (String)
                                        view.getBucketCombo()
                                                .getSelectedItem();

                        log.debug(
                                "[BUCKET LOAD RESULT] repository={} buckets={} previous={} selected={}",
                                repository.getId(),
                                allBuckets,
                                previousBucket,
                                selectedBucket);

                    } finally {

                        suppressBucketSelectionEvent = false;

                        pendingBucketSelection = null;
                    }

                    if (Objects.equals(
                            previousBucket,
                            selectedBucket)
                            && !forceBucketReload) {

                        log.debug(
                                "[BUCKET LOAD] bucket unchanged={} - refreshing tree/table",
                                selectedBucket);

                        hideOperationDialog(
                                OperationDialogType.BUCKET);

                        if (selectedBucket != null) {

                            contentLoader.invalidate(
                                    selectedBucket,
                                    S3TreeNode.ROOT_PREFIX);

                            loadRootFolders(
                                    selectedBucket);
                        }

                        return;
                    }

                    forceBucketReload = false;

                    if (selectedBucket != null) {

                        log.debug(
                                "[BUCKET LOAD] bucket changed {} -> {} - loading explorer",
                                previousBucket,
                                selectedBucket);

                        loadRootFolders(
                                selectedBucket);
                    }
                });

            } catch (Exception ex) {

                log.error(
                        "[BUCKET LOAD] failed: {}",
                        S3ErrorResolver.getDetailedMessage(ex),
                        ex);

                SwingUtilities.invokeLater(() -> {

                    if (operationId !=
                            operationGeneration.get()) {

                        return;
                    }

                    RepositoryDefinition active =
                            context.getActiveRepository();

                    if (active == null
                            || active.isEmpty()
                            || !Objects.equals(
                            active.getId(),
                            selectedRepository.getId())) {

                        return;
                    }

                    hideOperationDialog(
                            OperationDialogType.BUCKET);

                    pendingBucketSelection = null;

                    view.getFileTableModel().setFiles(
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
        loadFiles(
                bucket,
                prefix,
                false);
    }

    private void loadFiles(
            String bucket,
            String prefix,
            boolean restoreFocus) {
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
                generation,
                bucket,
                prefix,
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
                                generation,
                                bucket,
                                prefix,
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

                            if (!pasteSelectionCollectionInProgress) {

                                if (pendingFileTableSelectionKeys != null
                                        && !pendingFileTableSelectionKeys.isEmpty()) {

                                    restorePendingPasteSelection();

                                } else if (pendingFileTableSelectionKey != null) {

                                    restoreFileTableSelectionByKey(
                                            pendingFileTableSelectionKey);

                                    pendingFileTableSelectionKey =
                                            null;

                                } else if (preservedFileTableSelectionKeys != null
                                        && !preservedFileTableSelectionKeys.isEmpty()) {

                                    List<String> preservedKeys =
                                            new ArrayList<>(
                                                    preservedFileTableSelectionKeys);

                                    restoreFileTableSelectionByKeys(
                                            preservedKeys);

                                    preservedFileTableSelectionKeys =
                                            null;

                                    log.debug(
                                            "[FILE TABLE SELECTION] restored preserved selection keys={}",
                                            preservedKeys);
                                }
                            }

                            if (pendingDeleteSelectionViewRow >= 0) {

                                restoreFileTableSelectionAfterDelete();

                                pendingDeleteSelectionViewRow = -1;
                            }

                            if (restoreFocus
                                    || restoreFileTableFocus) {

                                restoreFileTableFocus();

                                restoreFileTableFocus = false;
                            }
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

            if (!pasteSelectionCollectionInProgress) {

                if (pendingFileTableSelectionKeys != null
                        && !pendingFileTableSelectionKeys.isEmpty()) {

                    restorePendingPasteSelection();

                } else if (pendingFileTableSelectionKey != null) {

                    restoreFileTableSelectionByKey(
                            pendingFileTableSelectionKey);

                    pendingFileTableSelectionKey =
                            null;

                } else if (preservedFileTableSelectionKeys != null
                        && !preservedFileTableSelectionKeys.isEmpty()) {

                    List<String> preservedKeys =
                            new ArrayList<>(
                                    preservedFileTableSelectionKeys);

                    restoreFileTableSelectionByKeys(
                            preservedKeys);

                    preservedFileTableSelectionKeys =
                            null;

                    log.debug(
                            "[FILE TABLE SELECTION] restored preserved selection keys={}",
                            preservedKeys);
                }
            }
        });
    }

    private void bindEvents() {

        view.getThemeCombo().addActionListener(e -> {

            UITheme theme =
                    (UITheme) view.getThemeCombo().getSelectedItem();

            themeManager.changeTheme(theme);

            if (themeSelectionListener != null) {

                themeSelectionListener.accept(theme);
            }
        });

        view.getRepositoryCombo().addActionListener(e -> {

            if (suppressRepositorySelectionEvent) {
                return;
            }

            RepositoryDefinition repository =
                    this.getCurrentRepository();

            if (repository == null
                    || repository == RepositoryDefinition.EMPTY_REPOSITORY) {

                encryptionConfig = null;

                view.updateEncryptionActionVisibility();

                setSelectedRepository(
                        RepositoryDefinition.EMPTY_REPOSITORY);

                return;
            }

            if (hasEncryptionConfiguration()) {

                encryptionConfig =
                        new EncryptionConfig(
                                repository.getEncryptionTransformation(),
                                repository.getEncryptionIv(),
                                repository.getEncryptionKey());

            } else {

                encryptionConfig = null;
            }

            view.updateEncryptionActionVisibility();

            if (repositorySelectionListener != null) {

                repositorySelectionListener.accept(
                        repository);
            }

            setSelectedRepository(
                    repository);
        });
        
        view.getBucketCombo().addActionListener(e -> {

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
        view.getFolderTree().addTreeSelectionListener(e -> {

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
                    "[TREE SELECTION] bucket={} prefix={} node={} currentFilePrefix={}",
                    bucket,
                    prefix,
                    selectedNode,
                    currentFilePrefix);

            /*
             * ---------------------------------------------------------
             * TREE MODEL REFRESH GUARD
             * ---------------------------------------------------------
             *
             * Tree modeli programatik olarak değişirken Swing bazen
             * TreeSelectionEvent üretebilir.
             *
             * Örneğin:
             *
             *     File Table = root
             *     Tree        = root
             *
             * SIL72 silinirken Tree'den node kaldırılır.
             *
             * Bu sırada root tekrar selection event'i üretirse
             * aynı root için loadFiles() çalıştırmak gereksizdir.
             *
             * Özellikle DELETE işleminde bu event:
             *
             *     nodesWereRemoved()
             *          -> TreeSelectionListener
             *          -> loadFiles()
             *
             * zincirini oluşturup File Table'ın tamamını yeniden
             * yüklemesine neden olabilir.
             *
             * Eğer Tree zaten File Table'da gösterilen prefix'i
             * gösteriyorsa tekrar yükleme yapma.
             * ---------------------------------------------------------
             */
            if (Objects.equals(
                    currentFileBucket,
                    bucket)
                    && Objects.equals(
                    currentFilePrefix,
                    prefix)) {

                log.info(
                        "[TREE SELECTION SKIP] " +
                                "same prefix already displayed; " +
                                "bucket={} prefix={}",
                        bucket,
                        prefix);

                return;
            }

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
                        view.getFolderTree()
                                .getSelectionPath();

                if (actualPath == null
                        || !actualPath.equals(
                        selectedPath)) {

                    log.warn(
                            "[TREE SELECTION] " +
                                    "selection changed before processing " +
                                    "prefix={}",
                            prefix);

                    return;
                }

                /*
                 * invokeLater() sonrasında da aynı prefix
                 * zaten File Table'da gösteriliyor olabilir.
                 *
                 * İkinci bir güvenlik kontrolü.
                 */
                if (Objects.equals(
                        currentFileBucket,
                        bucket)
                        && Objects.equals(
                        currentFilePrefix,
                        prefix)) {

                    log.info(
                            "[TREE SELECTION SKIP] " +
                                    "same prefix already displayed after EDT; " +
                                    "bucket={} prefix={}",
                            bucket,
                            prefix);

                    return;
                }

                loadFiles(
                        bucket,
                        prefix,
                        true);

                updateBreadcrumb(
                        prefix);

                updateActionStates();
            });
        });

        view.getFileTable()
                .getSelectionModel()
                .addListSelectionListener(e -> {

                    if (e.getValueIsAdjusting()) {
                        return;
                    }

                    updateActionStates();
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

        view.getFileTableModel().setFiles(
                Collections.emptyList());

        view.getBucketCombo().removeAllItems();

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
        view.getRepositoryCombo().removeAllItems();
        loadRepositoriesAsync();
    }

    public void reloadBuckets() {

        String previousBucket =
                getCurrentBucket();

        pendingBucketSelection =
                previousBucket;

        view.getBucketCombo().removeAllItems();

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
        int viewRow = view.getFileTable().getSelectedRow();
        if (viewRow < 0) {
            return null;
        }

        int modelRow = view.getFileTable().convertRowIndexToModel(viewRow);
        return view.getFileTableModel().getItem(modelRow);
    }

    private void openSelectedFileItem() {

        log.info(
                "[FILE TABLE OPEN] invoked selectedRow={} rowCount={}",
                view.getFileTable().getSelectedRow(),
                view.getFileTable().getSelectedRowCount());

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
                    view.getFileTable()::requestFocusInWindow);

            return;
        }

        downloadSelected();
    }

    private void navigateToFolder(S3FileItem item) {

        if (item == null) {
            return;
        }

        String bucket =
                getCurrentBucket();

        if (bucket == null) {
            return;
        }

        String targetPrefix;

        if (item.isParentFolder()) {

            /*
             * Şu an bulunduğumuz klasörü sakla.
             *
             * Örnek:
             *
             * currentFilePrefix = SIL3/DOWNLOAD/
             *
             * parent'a çıktığımızda:
             *
             * SIL3/
             *   DOWNLOAD/  <- selected
             */
            String currentPrefix =
                    currentFilePrefix;

            if (currentPrefix == null
                    || currentPrefix.isBlank()
                    || S3TreeNode.ROOT_PREFIX.equals(currentPrefix)) {

                return;
            }

            targetPrefix =
                    S3Util.extractParentPrefix(
                            currentPrefix);

            /*
             * Parent File Table yüklendiğinde
             * az önce çıktığımız klasörü seç.
             */
            pendingFileTableSelectionKey =
                    currentPrefix;

            restoreFileTableFocus =
                    true;

            log.info(
                    "[PARENT NAV] FILE TABLE parent item restore selection key={} parentPrefix={}",
                    pendingFileTableSelectionKey,
                    targetPrefix);

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

        treeController.selectPrefix(
                targetPrefix);
    }
    
    private void startDownload(
            S3FileItem item,
            Path destination) {

        try {

            fileOperationController.download(
                    item,
                    destination);

        } catch (Exception ex) {

            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage()));
        }
    }

    private void createFolder() {

        String folderName =
                JOptionPane.showInputDialog(
                        this,
                        "Folder Name");

        if (folderName == null
                || folderName.isBlank()) {
            return;
        }

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        String repositoryId =
                this.getCurrentRepository().getId();

        String prefix =
                getCurrentPrefix();

        String folderKey =
                prefix + folderName + "/";

        /*
         * İşlem tamamlandığında File Table'da
         * oluşturulan klasörü seç.
         */
        pendingFileTableSelectionKey =
                folderKey;

        restoreFileTableFocus = true;

        log.info(
                "[CREATE FOLDER] key={} restoreFocus={}",
                folderKey,
                restoreFileTableFocus);

        explorerPool.submit(() -> {

            try {

                transferManager.submitCreateFolder(
                        repositoryId,
                        bucket,
                        folderKey,
                        folderKey);

            } catch (Exception ex) {

                /*
                 * İşlem başlatılamadıysa pending selection
                 * artık geçerli değil.
                 */
                SwingUtilities.invokeLater(() -> {

                    pendingFileTableSelectionKey = null;
                    restoreFileTableFocus = false;

                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage());
                });
            }
        });
    }

    private void uploadFile() {

        String repositoryId =
                this.getCurrentRepository().getId();

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        JFileChooser chooser =
                new JFileChooser();

        chooser.setMultiSelectionEnabled(true);

        chooser.setFileSelectionMode(
                JFileChooser.FILES_AND_DIRECTORIES);

        if (lastOpenedFolderToUpload != null) {

            chooser.setCurrentDirectory(
                    lastOpenedFolderToUpload);
        }

        int result =
                chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String prefix =
                getCurrentPrefix();

        File[] files =
                chooser.getSelectedFiles();

        for (File file : files) {

            try {

                /*
                 * -------------------------------------------------
                 * DOSYA
                 * -------------------------------------------------
                 */
                if (file.isFile()) {

                    String objectKey =
                            prefix + file.getName();

                    pendingFileTableSelectionKey =
                            objectKey;

                    restoreFileTableFocus =
                            true;

                    log.info(
                            "[UPLOAD FILE] pending selection key={} restoreFocus={}",
                            pendingFileTableSelectionKey,
                            restoreFileTableFocus);

                    lastOpenedFolderToUpload =
                            file.getParentFile();

                    transferManager.submitUpload(
                            repositoryId,
                            bucket,
                            objectKey,
                            file.toPath(),
                            file.length());

                }

                /*
                 * -------------------------------------------------
                 * KLASÖR
                 * -------------------------------------------------
                 */
                else {

                    pendingFileTableSelectionKey =
                            S3Util.combineKey(
                                    prefix,
                                    file.getName())
                                    + "/";

                    restoreFileTableFocus =
                            true;

                    log.info(
                            "[UPLOAD FOLDER] pending selection key={} restoreFocus={}",
                            pendingFileTableSelectionKey,
                            restoreFileTableFocus);

                    lastOpenedFolderToUpload =
                            file;

                    transferManager.submitFolderUpload(
                            repositoryId,
                            bucket,
                            prefix,
                            file.toPath());
                }

            } catch (Exception ex) {

                /*
                 * İşlem submit edilemediyse
                 * bekleyen selection artık geçerli değil.
                 */
                pendingFileTableSelectionKey =
                        null;

                restoreFileTableFocus =
                        false;

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage()));
            }
        }
    }

    private void uploadFileEncrypted() {

        String repositoryId =
                this.getCurrentRepository().getId();

        String bucket =
                this.getCurrentBucket();

        if (bucket == null
                || encryptionConfig == null) {
            return;
        }

        JFileChooser chooser =
                new JFileChooser();

        chooser.setMultiSelectionEnabled(true);

        chooser.setFileSelectionMode(
                JFileChooser.FILES_ONLY);

        if (lastOpenedFolderToUpload != null) {

            chooser.setCurrentDirectory(
                    lastOpenedFolderToUpload);
        }

        int result =
                chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String prefix =
                getCurrentPrefix();

        File[] files =
                chooser.getSelectedFiles();

        for (File file : files) {

            try {

                String objectKey =
                        prefix + file.getName();

                pendingFileTableSelectionKey =
                        objectKey;

                restoreFileTableFocus =
                        true;

                log.info(
                        "[UPLOAD ENCRYPTED FILE] pending selection key={} restoreFocus={}",
                        pendingFileTableSelectionKey,
                        restoreFileTableFocus);

                lastOpenedFolderToUpload =
                        file.getParentFile();

                transferManager.submitUploadEncrypted(
                        repositoryId,
                        bucket,
                        objectKey,
                        file.toPath(),
                        file.length(),
                        encryptionConfig);

            } catch (Exception ex) {

                pendingFileTableSelectionKey = null;
                restoreFileTableFocus = false;

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
        view.getThemeCombo().setSelectedItem(UIThemeManager.getThemeByName(themeName));
    }

    public void selectRepository(RepositoryDefinition repository) {
        pendingRepositorySelection = repository;
        view.getRepositoryCombo().setSelectedItem(repository);
    }

    public void selectBucket(String bucketName) {
        pendingBucketSelection = bucketName;
        if (bucketName == null) {
            return;
        }
        view.getBucketCombo().setSelectedItem(bucketName);
    }

    public void onTransferEvent(
            TransferRuntime runtime) {

        if (runtime == null
                || runtime.getStatus()
                != TransferStatus.COMPLETED) {

            return;
        }

        TransferTask completedTask =
                runtime.getTask();

        if (completedTask == null) {
            return;
        }

        /*
         * ---------------------------------------------------------
         * GROUP TASK RESULT COLLECTOR
         * ---------------------------------------------------------
         *
         * Çoklu COPY / MOVE / DELETE işlemlerinde
         * TransferGroup başarılı task'ları kendi içinde
         * saklamıyor.
         *
         * Bu nedenle tamamlanan task'ı burada, EDT'ye
         * geçmeden önce topluyoruz.
         *
         * ÖNEMLİ:
         *
         * Group completion callback'i, son task'ın
         * SwingUtilities.invokeLater() içindeki kodu
         * çalışmadan önce gelebilir.
         *
         * Bu yüzden collector kesinlikle invokeLater()
         * dışında olmalıdır.
         */
        TransferGroup taskGroup =
                completedTask.getGroup();

        if (taskGroup != null) {

            completedGroupTasks
                    .computeIfAbsent(
                            taskGroup.getId(),
                            id -> new CopyOnWriteArrayList<>())
                    .add(completedTask);

            log.debug(
                    "[EXPLORER GROUP TASK COLLECT] " +
                            "group={} task={} type={} collectedCount={}",
                    taskGroup.getDisplayName(),
                    completedTask.getObjectKey(),
                    completedTask.getType(),
                    completedGroupTasks
                            .get(taskGroup.getId())
                            .size());
        }

        SwingUtilities.invokeLater(() -> {

            TransferTask task =
                    completedTask;

            /*
             * ---------------------------------------------------------
             * GROUP TASK
             * ---------------------------------------------------------
             */
            if (task.getGroup() != null) {

                TransferGroup group =
                        task.getGroup();

                /*
                 * -----------------------------------------------------
                 * TEK DOSYA COPY
                 * -----------------------------------------------------
                 *
                 * Tekli COPY artık group kullanmadığı normal akışta
                 * buraya düşmeyecek.
                 *
                 * Eski group'lu tekli COPY için de güvenli fallback
                 * olarak korunuyor.
                 */
                if (task.getType() == TransferType.COPY
                        && !group.isSourceFolder()
                        && group.getDetected() == 1) {

                    addCopiedFileToCurrentTable(task);
                }

                log.debug(
                        "[EXPLORER REFRESH] grouped task completed; " +
                                "refresh deferred until group completion. " +
                                "task={} group={}",
                        task.getObjectKey(),
                        group.getDisplayName());

                return;
            }

            /*
             * ---------------------------------------------------------
             * RENAME
             * ---------------------------------------------------------
             */
            if (task.getType() == TransferType.RENAME
                    || task.getType() == TransferType.RENAME_GROUP) {

                log.debug(
                        "[EXPLORER TRANSFER EVENT] "
                                + "rename task - incremental group completion "
                                + "will update Explorer. "
                                + "type={} objectKey={}",
                        task.getType(),
                        task.getObjectKey());

                return;
            }

            Set<RefreshTreeNode> affectedPrefixes =
                    task.getAffectedPrefixes();

            /*
             * ---------------------------------------------------------
             * FILE TABLE
             * ---------------------------------------------------------
             */
            if (task.isAffectsObjectList()) {

                /*
                 * Tekli DELETE:
                 *
                 * Group kullanılmadığı için burada doğrudan
                 * incremental remove yapılır.
                 */
                if (task.getType() == TransferType.DELETE) {

                    boolean removed =
                            view.getFileTableModel()
                                    .removeFileByKey(
                                            task.getObjectKey());

                    log.info(
                            "[FILE TABLE DELETE REMOVE] " +
                                    "key={} removed={}",
                            task.getObjectKey(),
                            removed);

                    return;
                }

                if (task.getType()
                        == TransferType.CREATE_FOLDER) {

                    addFolderToCurrentFileTable(
                            task.getBucket(),
                            task.getObjectKey());

                }
                else if (task.getType()
                        == TransferType.UPLOAD) {

                    addUploadedFileToCurrentFileTable(task);

                }
                else if (task.getType() == TransferType.COPY
                        || task.getType() == TransferType.MOVE) {

                    addCopiedFileToCurrentTable(task);

                }
                else {

                    refreshScheduler
                            .scheduleCurrentTableRefresh();
                }
            }

            /*
             * ---------------------------------------------------------
             * FOLDER TREE
             * ---------------------------------------------------------
             */
            if (task.isAffectsFolderTree()
                    && affectedPrefixes != null
                    && !affectedPrefixes.isEmpty()) {

                refreshScheduler.scheduleRefresh(
                        affectedPrefixes);
            }
        });
    }

    private void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {

        if (event == null) {
            return;
        }

        TransferGroup group =
                event.getGroup();

        if (group == null) {
            return;
        }

        log.debug(
                "[EXPLORER GROUP COMPLETED] " +
                        "group={} operation={} " +
                        "successful={} " +
                        "sourceRefreshRequired={} " +
                        "sourceIsFolder={}",
                group.getDisplayName(),
                group.getOperation(),
                event.isSuccessful(),
                event.isSourceRefreshRequired(),
                group.isSourceFolder());

        List<TransferTask> completedTasks =
                completedGroupTasks.remove(
                        group.getId());

        if (completedTasks == null) {
            completedTasks =
                    List.of();
        }

        log.info(
                "[EXPLORER GROUP TASKS] group={} collectedTasks={} detected={}",
                group.getDisplayName(),
                completedTasks.size(),
                group.getDetected());
        
        String currentBucket =
                currentFileBucket;

        String currentPrefix =
                currentFilePrefix;

        log.info(
                "[EXPLORER GROUP REFRESH STATE] " +
                        "currentFileBucket={} " +
                        "currentFilePrefix={} " +
                        "currentBucket={} " +
                        "currentPrefix={} " +
                        "targetBucket={} " +
                        "targetPrefix={} " +
                        "sourceIsFolder={}",
                currentFileBucket,
                currentFilePrefix,
                currentBucket,
                currentPrefix,
                group.getTargetBucket(),
                group.getTargetPrefix(),
                group.isSourceFolder());

        /*
         * =========================================================
         * INCREMENTAL MULTI FILE OPERATIONS
         * =========================================================
         *
         * Group içindeki bütün başarılı task'lar artık burada
         * elimizde.
         *
         * File Table reload edilmez.
         */
        if (!completedTasks.isEmpty()
                && (group.getOperation() == TransferType.COPY_GROUP
                || group.getOperation() == TransferType.MOVE_GROUP
                || group.getOperation() == TransferType.DELETE_GROUP)) {

            for (TransferTask task : completedTasks) {

                if (task == null) {
                    continue;
                }

                /*
                 * -----------------------------------------------------
                 * MULTI DELETE
                 * -----------------------------------------------------
                 */
                if (task.getType() == TransferType.DELETE) {

                    if (Objects.equals(
                            currentBucket,
                            task.getBucket())
                            && Objects.equals(
                            currentPrefix,
                            getParentPrefix(
                                    task.getObjectKey()))) {

                        boolean removed =
                                view.getFileTableModel()
                                        .removeFileByKey(
                                                task.getObjectKey());

                        log.info(
                                "[FILE TABLE GROUP DELETE REMOVE] " +
                                        "key={} removed={} group={}",
                                task.getObjectKey(),
                                removed,
                                group.getDisplayName());
                    }

                    continue;
                }

                /*
                 * -----------------------------------------------------
                 * MULTI COPY / MOVE - TARGET
                 * -----------------------------------------------------
                 */
                if (task.getType() == TransferType.COPY
                        || task.getType() == TransferType.MOVE) {

                    String targetBucket =
                            task.getTargetBucket();

                    String targetKey =
                            task.getTargetObjectKey();

                    if (targetBucket == null
                            || targetKey == null
                            || targetKey.isBlank()) {

                        continue;
                    }

                    String targetParentPrefix =
                            getParentPrefix(targetKey);

                    if (Objects.equals(
                            currentBucket,
                            targetBucket)
                            && Objects.equals(
                            currentPrefix,
                            targetParentPrefix)) {

                        boolean targetIsFolder =
                                targetKey != null && targetKey.endsWith("/");

                        S3FileItem item =
                                new S3FileItem(
                                        task.getTargetRepositoryId(),
                                        targetBucket,
                                        targetKey,
                                        task.getSize(),
                                        null,
                                        null,
                                        targetIsFolder);

                        boolean added =
                                view.getFileTableModel()
                                        .addFile(item);

                        log.info(
                                "[FILE TABLE GROUP INSERT] " +
                                        "source={}/{} target={}/{} " +
                                        "size={} added={} group={}",
                                task.getBucket(),
                                task.getObjectKey(),
                                targetBucket,
                                targetKey,
                                task.getSize(),
                                added,
                                group.getDisplayName());
                    }

                    /*
                     * -------------------------------------------------
                     * MULTI MOVE - SOURCE
                     * -------------------------------------------------
                     *
                     * MOVE'd edilen kaynak File Table'da açıksa
                     * kaynak satırı da incremental kaldır.
                     */
                    if (task.getType() == TransferType.MOVE
                            && Objects.equals(
                            currentBucket,
                            task.getBucket())
                            && Objects.equals(
                            currentPrefix,
                            getParentPrefix(
                                    task.getObjectKey()))) {

                        boolean removed =
                                view.getFileTableModel()
                                        .removeFileByKey(
                                                task.getObjectKey());

                        log.info(
                                "[FILE TABLE GROUP MOVE REMOVE] " +
                                        "key={} removed={} group={}",
                                task.getObjectKey(),
                                removed,
                                group.getDisplayName());
                    }
                }
            }

            /*
             * ---------------------------------------------------------
             * MULTI DELETE SELECTION RESTORE
             * ---------------------------------------------------------
             *
             * DELETE group tamamlandığında bütün silinen satırlar
             * incremental olarak File Table'dan kaldırılmış olur.
             *
             * Silme öncesinde pendingDeleteSelectionViewRow ile
             * ilk seçili satırın view index'i saklanmıştır.
             *
             * Şimdi:
             *
             *     1. bütün silinen satırlar kaldırıldı
             *     2. yeni rowCount hesaplanır
             *     3. mümkünse aynı view row seçilir
             *     4. eğer o satır artık mevcut değilse son satır seçilir
             *
             * Böylece:
             *
             *     [1] [2] [3] [4] [5]
             *          ^^^^^^^^^^^^^
             *          DELETE
             *
             * sonrasında:
             *
             *     [1] [5]
             *          ^
             *          selection
             *
             * veya silinenlerin arasında başka satırlar varsa
             * mümkün olduğunca eski konuma yakın satır seçilir.
             */
            if (group.getOperation()
                    == TransferType.DELETE_GROUP
                    && pendingDeleteSelectionViewRow >= 0) {

                SwingUtilities.invokeLater(() -> {

                    int rowCount =
                            view.getFileTable().getRowCount();

                    int targetViewRow =
                            Math.min(
                                    pendingDeleteSelectionViewRow,
                                    rowCount - 1);

                    log.info(
                            "[DELETE GROUP SELECTION RESTORE] " +
                                    "pendingRow={} rowCount={} targetRow={}",
                            pendingDeleteSelectionViewRow,
                            rowCount,
                            targetViewRow);

                    if (targetViewRow >= 0) {

                        JTable table =
                                view.getFileTable();

                        table.clearSelection();

                        table.setRowSelectionInterval(
                                targetViewRow,
                                targetViewRow);

                        table.scrollRectToVisible(
                                table.getCellRect(
                                        targetViewRow,
                                        0,
                                        true));

                        restoreFileTableFocus();

                        log.info(
                                "[DELETE GROUP SELECTION RESTORE] " +
                                        "success targetViewRow={} selectedRowCount={}",
                                targetViewRow,
                                table.getSelectedRowCount());

                    } else {

                        /*
                         * Tablo tamamen boşaldıysa selection yapılamaz.
                         */
                        log.info(
                                "[DELETE GROUP SELECTION RESTORE] " +
                                        "table empty; no selection");

                        restoreFileTableFocus();
                    }

                    pendingDeleteSelectionViewRow = -1;

                    updateActionStates();
                });
            }
            
            /*
             * ---------------------------------------------------------
             * GROUP SELECTION RESTORE
             * ---------------------------------------------------------
             *
             * Bütün task'lar File Table'a eklendikten sonra
             * selection yalnızca BİR KEZ restore edilir.
             *
             * Böylece ilk kopyalanan dosyada selection restore edilip
             * sonraki dosyaların akışı bozulmaz.
             */
            if (pendingFileTableSelectionKeys != null
                    && !pendingFileTableSelectionKeys.isEmpty()) {

                SwingUtilities.invokeLater(() -> {

                    if (restorePendingPasteSelection()) {

                        restoreFileTableFocus();

                        log.info(
                                "[FILE TABLE GROUP SELECTION] " +
                                        "restored keys={}",
                                pendingFileTableSelectionKeys);
                    }
                });
            }

            /*
             * ---------------------------------------------------------
             * GROUP FILE OPERATION TAMAMLANDI
             * ---------------------------------------------------------
             *
             * Tree tarafındaki mevcut group refresh mekanizmasının
             * devam etmesine izin ver.
             */
        }
        
        /*
         * =========================================================
         * RENAME
         * =========================================================
         *
         * Rename iki şekilde gelebilir:
         *
         *     TransferType.RENAME
         *     TransferType.RENAME_GROUP
         *
         * Bu nedenle ikisini de burada özel olarak ele alıyoruz.
         *
         * DOSYA RENAME:
         *
         *     SIL71/1.json
         *          ->
         *     SIL71/2.json
         *
         * File Table'daki mevcut satır yerinde değiştirilir.
         *
         * Folder Tree'ye kesinlikle dokunulmaz.
         *
         *
         * KLASÖR RENAME:
         *
         *     SIL71/
         *          ->
         *     SIL72/
         *
         * File Table:
         *     SIL71/ remove
         *     SIL72/ insert
         *
         * Folder Tree:
         *     mevcut SIL71 node'u SIL72 olarak rename edilir.
         *
         * Aynı Tree node'u korunduğu için altındaki child node'lar,
         * cache kayıtları ve expansion state korunur.
         * =========================================================
         */
        if (group.getOperation()
                == TransferType.RENAME
                || group.getOperation()
                == TransferType.RENAME_GROUP) {

            String sourceKey =
                    event.getPrefix();

            String targetKey =
                    group.getTargetPrefix();

            String sourceBucket =
                    event.getBucket();

            String targetBucket =
                    group.getTargetBucket();

            boolean sourceIsFolder =
                    group.isSourceFolder();

            log.info(
                    "[EXPLORER RENAME] source={} target={} folder={}",
                    sourceKey,
                    targetKey,
                    sourceIsFolder);

            /*
             * -----------------------------------------------------
             * FILE TABLE - SOURCE
             * -----------------------------------------------------
             */
            if (Objects.equals(
                    currentBucket,
                    sourceBucket)) {

                String sourceParentPrefix =
                        getParentPrefix(sourceKey);

                /*
                 * Kaynak item şu anda açık olan File Table'ın
                 * içinde gösteriliyorsa işlem yap.
                 */
                if (Objects.equals(
                        currentPrefix,
                        sourceParentPrefix)) {

                    if (sourceIsFolder) {

                        /*
                         * -----------------------------------------
                         * KLASÖR RENAME
                         * -----------------------------------------
                         *
                         * Klasörün mevcut satırını kaldırıyoruz.
                         * Hedef satır aşağıda tekrar eklenecek.
                         */
                        boolean removed =
                                view.getFileTableModel()
                                        .removeFileByKey(
                                                sourceKey);

                        log.info(
                                "[FILE TABLE RENAME REMOVE] " +
                                        "folder key={} removed={}",
                                sourceKey,
                                removed);

                    } else {

                        /*
                         * -----------------------------------------
                         * DOSYA RENAME
                         * -----------------------------------------
                         *
                         * Satırı silip tekrar eklemiyoruz.
                         *
                         * Aynı satırdaki S3FileItem değiştirilir.
                         * Böylece dosyanın sıra konumu korunur.
                         */
                        int sourceRow =
                                view.getFileTableModel()
                                        .findRowByKey(
                                                sourceKey);

                        if (sourceRow < 0) {

                            log.warn(
                                    "[FILE TABLE RENAME] " +
                                            "source file row not found " +
                                            "source={}",
                                    sourceKey);

                        } else {

                            S3FileItem sourceItem =
                                    view.getFileTableModel()
                                            .getItem(
                                                    sourceRow);

                            if (sourceItem == null) {

                                log.warn(
                                        "[FILE TABLE RENAME] " +
                                                "source file item is null " +
                                                "source={}",
                                        sourceKey);

                            } else {

                                S3FileItem renamedItem =
                                        new S3FileItem(
                                                sourceItem.getRepositoryId(),
                                                targetBucket,
                                                targetKey,
                                                sourceItem.getSize(),
                                                sourceItem.getLastModified(),
                                                sourceItem.getStorageClass(),
                                                false);

                                boolean replaced =
                                        view.getFileTableModel()
                                                .replaceFileByKey(
                                                        sourceKey,
                                                        renamedItem);

                                log.info(
                                        "[FILE TABLE RENAME REPLACE] " +
                                                "source={} target={} " +
                                                "replaced={} row={}",
                                        sourceKey,
                                        targetKey,
                                        replaced,
                                        sourceRow);
                            }
                        }
                    }
                }
            }

            /*
             * -----------------------------------------------------
             * FILE TABLE - TARGET
             * -----------------------------------------------------
             *
             * Sadece KLASÖR rename'inde ayrıca hedef klasör
             * File Table'a eklenir.
             *
             * Dosya rename'inde yukarıdaki replaceFileByKey()
             * zaten işlemi tamamladı.
             */
            if (sourceIsFolder
                    && Objects.equals(
                    currentBucket,
                    targetBucket)) {

                String targetParentPrefix =
                        getParentPrefix(targetKey);

                if (Objects.equals(
                        currentPrefix,
                        targetParentPrefix)) {

                    addFolderToCurrentFileTable(
                            targetBucket,
                            targetKey);

                    log.info(
                            "[FILE TABLE RENAME INSERT] " +
                                    "folder key={}",
                            targetKey);
                }
            }

            /*
             * -----------------------------------------------------
             * FOLDER TREE
             * -----------------------------------------------------
             *
             * DOSYA RENAME:
             *
             *     Tree'ye hiçbir şey yapma.
             *
             * Çünkü dosya Folder Tree'nin node'u değildir.
             *
             * KLASÖR RENAME:
             *
             *     Mevcut Tree node'unu yerinde rename et.
             *
             * Böylece:
             *
             *     SIL71/
             *       └── A/
             *           └── B/
             *
             * rename sonrasında:
             *
             *     SIL72/
             *       └── A/
             *           └── B/
             *
             * olarak aynı node nesneleriyle korunur.
             */
            if (sourceIsFolder) {

                boolean renamed =
                        false;

                if (Objects.equals(
                        currentBucket,
                        sourceBucket)) {

                    renamed =
                            treeController
                                    .renameNodePreservingChildren(
                                            sourceKey,
                                            targetKey);
                }

                log.info(
                        "[TREE RENAME PRESERVED] " +
                                "source={} target={} success={}",
                        sourceKey,
                        targetKey,
                        renamed);

            } else {

                log.debug(
                        "[TREE RENAME SKIP] " +
                                "file rename source={} target={}",
                        sourceKey,
                        targetKey);
            }

            /*
             * -----------------------------------------------------
             * RENAME SONRASI SELECTION
             * -----------------------------------------------------
             */
            if (pendingFileTableSelectionKey != null) {

                String selectionKey =
                        pendingFileTableSelectionKey;

                SwingUtilities.invokeLater(() -> {

                    restoreFileTableSelectionByKey(
                            selectionKey);

                    if (restoreFileTableFocus) {
                        restoreFileTableFocus();
                    }
                });
            }

            /*
             * -----------------------------------------------------
             * ÇOK ÖNEMLİ
             * -----------------------------------------------------
             *
             * Rename işlemi burada tamamen biter.
             *
             * Normal TARGET REFRESH bölümüne kesinlikle
             * düşülmemelidir.
             *
             * Özellikle dosya rename'inde:
             *
             *     SIL71/2.json
             *
             * Folder Tree'ye ADD olarak gönderilmemelidir.
             */
            return;
        }

        /*
         * =========================================================
         * DELETE GROUP - FOLDER TREE
         * =========================================================
         */
        if (group.getOperation()
                == TransferType.DELETE_GROUP
                && event.isSourceRefreshRequired()) {

            List<RefreshTreeNode> deleteRefreshes =
                    new ArrayList<>();

            for (TransferTask task : completedTasks) {

                if (task == null
                        || task.getType() != TransferType.DELETE) {
                    continue;
                }

                String objectKey =
                        task.getObjectKey();

                if (objectKey == null
                        || !objectKey.endsWith("/")) {
                    continue;
                }

                if (!Objects.equals(
                        currentBucket,
                        task.getBucket())) {
                    continue;
                }

                deleteRefreshes.add(
                        new RefreshTreeNode(
                                objectKey,
                                RefreshTreeOperation.DELETE));
            }

            if (!deleteRefreshes.isEmpty()) {

                log.info(
                        "[EXPLORER GROUP DELETE TREE REFRESH] " +
                                "group={} prefixes={}",
                        group.getDisplayName(),
                        deleteRefreshes);

                refreshScheduler.scheduleRefresh(
                        deleteRefreshes);
            }
        }

        /*
         * =========================================================
         * NORMAL SOURCE REFRESH
         * =========================================================
         *
         * DELETE_GROUP buraya girmez.
         */
        /*
         * =========================================================
         * NORMAL SOURCE REFRESH
         * =========================================================
         *
         * DELETE_GROUP kendi özel bloğunda işlenir.
         *
         * MOVE_GROUP'da ise source tarafını
         * group.isSourceFolder() üzerinden belirleyemeyiz.
         * Çünkü aynı group içinde:
         *
         *     klasör
         *     klasör
         *     dosya
         *
         * gibi karışık seçim olabilir.
         *
         * Bu nedenle tamamlanan task'ların kendisine bakıyoruz.
         */
        if (event.isSourceRefreshRequired()
                && group.getOperation()
                != TransferType.DELETE_GROUP) {

            /*
             * -------------------------------------------------
             * MOVE GROUP - FOLDER TREE
             * -------------------------------------------------
             *
             * Her MOVE task'ı içerisinde gerçek source object key
             * bulunuyor.
             *
             * Klasör ise:
             *
             *     sourceKey.endsWith("/")
             *
             * olur.
             *
             * Folder Tree'den tam olarak bu node silinir.
             */
            if (group.getOperation()
                    == TransferType.MOVE_GROUP) {

                List<RefreshTreeNode> sourceTreeRefreshes =
                        new ArrayList<>();

                for (TransferTask task : completedTasks) {

                    if (task == null
                            || task.getType()
                            != TransferType.MOVE) {
                        continue;
                    }

                    String sourceKey =
                            task.getObjectKey();

                    if (sourceKey == null
                            || !sourceKey.endsWith("/")) {
                        continue;
                    }

                    if (!Objects.equals(
                            currentBucket,
                            task.getBucket())) {
                        continue;
                    }

                    sourceTreeRefreshes.add(
                            new RefreshTreeNode(
                                    sourceKey,
                                    RefreshTreeOperation.DELETE));
                }

                if (!sourceTreeRefreshes.isEmpty()) {

                    log.info(
                            "[EXPLORER SOURCE FOLDER TREE REFRESH] "
                                    + "group={} operation={} prefixes={}",
                            group.getDisplayName(),
                            group.getOperation(),
                            sourceTreeRefreshes);

                    refreshScheduler.scheduleRefresh(
                            sourceTreeRefreshes);
                }

                /*
                 * -------------------------------------------------
                 * MOVE GROUP - FILE TABLE SOURCE
                 * -------------------------------------------------
                 *
                 * File Table zaten group tamamlandığında
                 * incremental olarak güncelleniyor.
                 *
                 * Burada yalnızca legacy davranışı korumak
                 * için source dosya/klasör satırlarını kaldırıyoruz.
                 */
                for (TransferTask task : completedTasks) {

                    if (task == null
                            || task.getType()
                            != TransferType.MOVE) {
                        continue;
                    }

                    String sourceKey =
                            task.getObjectKey();

                    if (sourceKey == null) {
                        continue;
                    }

                    if (!Objects.equals(
                            currentBucket,
                            task.getBucket())) {
                        continue;
                    }

                    if (!Objects.equals(
                            currentPrefix,
                            getParentPrefix(sourceKey))) {
                        continue;
                    }

                    boolean removed =
                            view.getFileTableModel()
                                    .removeFileByKey(sourceKey);

                    log.info(
                            "[FILE TABLE GROUP MOVE REMOVE] "
                                    + "source={} removed={} group={}",
                            sourceKey,
                            removed,
                            group.getDisplayName());
                }

            } else {

                /*
                 * -------------------------------------------------
                 * NORMAL / LEGACY SOURCE REFRESH
                 * -------------------------------------------------
                 *
                 * Tek tip source operasyonlarında mevcut
                 * davranışı koruyoruz.
                 */
                String sourceBucket =
                        event.getBucket();

                String sourcePrefix =
                        event.getPrefix();

                if (group.isSourceFolder()) {

                    String sourceParentPrefix =
                            getParentPrefix(sourcePrefix);

                    if (Objects.equals(
                            currentBucket,
                            sourceBucket)) {

                        log.debug(
                                "[EXPLORER SOURCE TREE REFRESH] "
                                        + "prefix={} operation=DELETE",
                                sourcePrefix);

                        refreshScheduler.scheduleRefresh(
                                List.of(
                                        new RefreshTreeNode(
                                                sourcePrefix,
                                                RefreshTreeOperation.DELETE)));
                    }

                    if (Objects.equals(
                            currentBucket,
                            sourceBucket)
                            && Objects.equals(
                            currentPrefix,
                            sourceParentPrefix)) {

                        boolean removed =
                                view.getFileTableModel()
                                        .removeFileByKey(
                                                sourcePrefix);

                        log.info(
                                "[FILE TABLE ROW REMOVE] "
                                        + "key={} removed={}",
                                sourcePrefix,
                                removed);

                        if (removed
                                && pendingDeleteSelectionViewRow >= 0) {

                            SwingUtilities.invokeLater(() -> {

                                log.info(
                                        "[DELETE SELECTION RESTORE TRIGGER] "
                                                + "pendingRow={} rowCount={}",
                                        pendingDeleteSelectionViewRow,
                                        view.getFileTable().getRowCount());

                                restoreFileTableSelectionAfterDelete();

                                restoreFileTableFocus();

                                pendingDeleteSelectionViewRow = -1;
                            });
                        }
                    }

                } else {

                    if (Objects.equals(
                            currentBucket,
                            sourceBucket)
                            && Objects.equals(
                            currentPrefix,
                            getParentPrefix(sourcePrefix))) {

                        boolean removed =
                                view.getFileTableModel()
                                        .removeFileByKey(
                                                sourcePrefix);

                        log.info(
                                "[FILE TABLE ROW REMOVE] "
                                        + "key={} removed={}",
                                sourcePrefix,
                                removed);
                    }
                }
            }
        }

        /*
         * =========================================================
         * TARGET REFRESH
         * =========================================================
         *
         * Buraya RENAME kesinlikle gelemez.
         *
         * Sadece:
         *
         *     COPY
         *     MOVE
         *     COPY_GROUP
         *     MOVE_GROUP
         *     UPLOAD_GROUP
         *
         * gibi operasyonlar hedef Tree'yi günceller.
         * =========================================================
         */
        if (group.getOperation()
                == TransferType.COPY
                || group.getOperation()
                == TransferType.MOVE
                || group.getOperation()
                == TransferType.COPY_GROUP
                || group.getOperation()
                == TransferType.MOVE_GROUP
                || group.getOperation()
                == TransferType.UPLOAD_GROUP) {

            String targetBucket =
                    group.getTargetBucket();

            if (targetBucket != null
                    && Objects.equals(
                    currentBucket,
                    targetBucket)) {

                List<RefreshTreeNode> targetTreeRefreshes =
                        new ArrayList<>();

                /*
                 * COPY_GROUP / MOVE_GROUP:
                 *
                 * group.getTargetPrefix()
                 * klasör + dosya karışık seçimde yalnızca
                 * parent prefix'i temsil eder.
                 *
                 * Folder Tree'ye gerçek oluşturulan klasör
                 * object key'ini göndermeliyiz.
                 */
                if (group.getOperation()
                        == TransferType.COPY_GROUP
                        || group.getOperation()
                        == TransferType.MOVE_GROUP) {

                    for (TransferTask task : completedTasks) {

                        if (task == null) {
                            continue;
                        }

                        String targetObjectKey =
                                task.getTargetObjectKey();

                        if (targetObjectKey == null
                                || !targetObjectKey.endsWith("/")) {
                            continue;
                        }

                        if (!Objects.equals(
                                currentBucket,
                                task.getTargetBucket())) {
                            continue;
                        }

                        targetTreeRefreshes.add(
                                new RefreshTreeNode(
                                        targetObjectKey,
                                        RefreshTreeOperation.ADD));
                    }

                } else {

                    String targetPrefix =
                            group.getTargetPrefix();

                    if (targetPrefix != null) {

                        String refreshPrefix =
                                group.getOperation()
                                        == TransferType.UPLOAD_GROUP
                                        ? getParentPrefix(targetPrefix)
                                        : targetPrefix;

                        targetTreeRefreshes.add(
                                new RefreshTreeNode(
                                        refreshPrefix,
                                        RefreshTreeOperation.ADD));
                    }
                }

                if (!targetTreeRefreshes.isEmpty()) {

                    log.info(
                            "[EXPLORER TARGET TREE REFRESH] " +
                                    "group={} operation={} prefixes={}",
                            group.getDisplayName(),
                            group.getOperation(),
                            targetTreeRefreshes);

                    refreshScheduler.scheduleRefresh(
                            targetTreeRefreshes);
                }
            }
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

        JTable table =
                view.getFileTable();

        /*
         * Preserve the current File Table selection before
         * the refresh clears the table model.
         *
         * This is intentionally independent from paste selection.
         * A normal refresh must not destroy a user's current selection.
         */
        List<String> currentSelectionKeys =
                getSelectedFileTableKeys();

        if (!currentSelectionKeys.isEmpty()) {

            preservedFileTableSelectionKeys =
                    new ArrayList<>(currentSelectionKeys);

            log.debug(
                    "[FILE TABLE REFRESH] preserving selection keys={}",
                    preservedFileTableSelectionKeys);
        }

        boolean restoreFocus =
                table.hasFocus()
                        || restoreFileTableFocus
                        || forceFileTableFocusAfterRefresh;

        log.debug(
                "[FILE TABLE REFRESH] bucket={} prefix={} restoreFocus={} tableFocus={} deleteRestore={} preservedSelection={}",
                bucket,
                prefix,
                restoreFocus,
                table.hasFocus(),
                restoreFileTableFocus,
                preservedFileTableSelectionKeys);

        contentLoader.invalidate(
                bucket,
                prefix);

        fileLoadGeneration.incrementAndGet();

        loadFiles(
                bucket,
                prefix,
                restoreFocus);

        restoreFileTableFocus = false;

        updateActionStates();
    }

    public void updateBreadcrumb(String prefix) {
        if (prefix == null) {
            prefix = getCurrentPrefix();
        }

        view.getBreadcrumbPanel().removeAll();

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

        view.getBreadcrumbPanel().revalidate();
        view.getBreadcrumbPanel().repaint();
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
        view.getBreadcrumbPanel().add(button);
    }

    public Color getContrastColor(Color color) {
        double yiq = (double) ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) / 1000;
        return (yiq >= 128) ? Color.BLACK : Color.WHITE;
    }

    private void navigateToPrefix(String prefix) {

        if (prefix == null) {
            return;
        }

        String bucket =
                this.getCurrentBucket();

        if (bucket == null) {
            return;
        }

        log.info(
                "[NAVIGATION] prefix={}",
                prefix);

        treeController.selectPrefix(prefix);
    }

    private void copySelected() {
        List<S3FileItem> items =
                getSelectedItems();

        if (items.isEmpty()) {
            return;
        }

        clipboardController.copy(items);

        updateActionStates();
    }

    private void moveSelected() {

        List<S3FileItem> items =
                getSelectedItems();

        if (items.isEmpty()) {
            return;
        }

        clipboardController.move(items);

        updateActionStates();
    }

    private void pasteClipboard() {
        if (pasteController == null) {
            return;
        }
        pasteController.paste();
    }

    private void copyText() {

        JTable table =
                view.getFileTable();

        int[] selectedRows =
                table.getSelectedRows();

        if (selectedRows.length == 0) {
            return;
        }

        StringBuilder text =
                new StringBuilder();

        for (int viewRow : selectedRows) {

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            S3FileItem item =
                    view.getFileTableModel()
                            .getItem(modelRow);

            if (item == null
                    || item.isParentFolder()) {
                continue;
            }

            if (text.length() > 0) {
                text.append(System.lineSeparator());
            }

            text.append(item.getKey());
        }

        if (text.length() == 0) {
            return;
        }

        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(
                        new java.awt.datatransfer.StringSelection(
                                text.toString()),
                        null);
    }

    private List<S3FileItem> getSelectedItems() {
        int[] viewRows = view.getFileTable().getSelectedRows();
        List<S3FileItem> items = new ArrayList<>();
        for (int row : viewRows) {
            S3FileItem item = view.getFileTableModel().getItem(view.getFileTable().convertRowIndexToModel(row));
            if (!item.isParentFolder()) {
                items.add(item);
            }
        }

        return items;
    }

    private RepositoryDefinition getCurrentRepository() {
        return (RepositoryDefinition) view.getRepositoryCombo().getSelectedItem();
    }

    private String getCurrentBucket() {
        return (String) view.getBucketCombo().getSelectedItem();
    }

    private String getCurrentPrefix() {
        return treeController.getSelectedPrefix();
    }

    private boolean exists(String key) {
        return view.getFileTableModel().getItems()
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
            chooser.setCurrentDirectory(
                    lastOpenedFolderToDownload);
        }

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY);

        int result =
                chooser.showSaveDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destination =
                chooser.getSelectedFile().toPath();

        lastOpenedFolderToDownload =
                destination.toFile();

        /*
         * Tek item veya klasör içeren seçimlerde
         * mevcut davranışı koruyoruz.
         */
        boolean allFiles =
                items.size() > 1
                        && items.stream()
                        .noneMatch(S3FileItem::isFolder);

        if (!allFiles) {

            for (S3FileItem item : items) {
                startDownload(
                        item,
                        destination);
            }

            return;
        }

        S3FileItem firstItem =
                items.getFirst();

        String repositoryId =
                firstItem.getRepositoryId();

        String bucket =
                getCurrentBucket();

        String sourcePrefix =
                getCurrentPrefix();

        if (repositoryId == null
                || bucket == null
                || sourcePrefix == null) {
            return;
        }

        TransferGroup group =
                transferManager.createDownloadGroup(
                        repositoryId,
                        bucket,
                        sourcePrefix,
                        getOperationGroupName(items),
                        destination);

        for (S3FileItem item : items) {

            try {

                fileOperationController.download(
                        item,
                        destination,
                        group);

            } catch (Exception ex) {

                log.error(
                        "[DOWNLOAD GROUP] failed source={} group={}",
                        item.getKey(),
                        group.getDisplayName(),
                        ex);

                group.failed();

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage(),
                                "Download Failed",
                                JOptionPane.ERROR_MESSAGE));
            }
        }

        group.markProductionCompleted();
    }

    public void downloadSelectedDecrypted() {

        List<S3FileItem> items =
                getSelectedItems();

        if (items.isEmpty()
                || encryptionConfig == null) {
            return;
        }

        JFileChooser chooser =
                new JFileChooser();

        if (lastOpenedFolderToDownload != null) {
            chooser.setCurrentDirectory(
                    lastOpenedFolderToDownload);
        }

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY);

        int result =
                chooser.showSaveDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destination =
                chooser.getSelectedFile().toPath();

        lastOpenedFolderToDownload =
                destination.toFile();

        /*
         * Tek item veya klasör içeren seçimlerde
         * mevcut davranışı koruyoruz.
         */
        boolean allFiles =
                items.size() > 1
                        && items.stream()
                        .noneMatch(S3FileItem::isFolder);

        if (!allFiles) {

            for (S3FileItem item : items) {

                try {

                    fileOperationController.downloadDecrypted(
                            item,
                            destination,
                            encryptionConfig);

                } catch (Exception ex) {

                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(
                                    this,
                                    ex.getMessage(),
                                    "Download Failed",
                                    JOptionPane.ERROR_MESSAGE));
                }
            }

            return;
        }

        S3FileItem firstItem =
                items.getFirst();

        String repositoryId =
                firstItem.getRepositoryId();

        String bucket =
                getCurrentBucket();

        String sourcePrefix =
                getCurrentPrefix();

        if (repositoryId == null
                || bucket == null
                || sourcePrefix == null) {
            return;
        }

        TransferGroup group =
                transferManager.createDownloadGroup(
                        repositoryId,
                        bucket,
                        sourcePrefix,
                        getOperationGroupName(items),
                        destination);

        for (S3FileItem item : items) {

            try {

                fileOperationController.downloadDecrypted(
                        item,
                        destination,
                        encryptionConfig,
                        group);

            } catch (Exception ex) {

                log.error(
                        "[DOWNLOAD DECRYPTED GROUP] failed source={} group={}",
                        item.getKey(),
                        group.getDisplayName(),
                        ex);

                group.failed();

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage(),
                                "Download Failed",
                                JOptionPane.ERROR_MESSAGE));
            }
        }

        group.markProductionCompleted();
    }

    private void showRepositoryManager() {

        RepositoryPanel panel =
                new RepositoryPanel(
                        repositoryManager,
                        clientFactory);

        JDialog dialog =
                new JDialog(
                        S3Util.getMainFrameAncestor(this),
                        "Repositories",
                        true);

        dialog.setContentPane(panel);
        dialog.setSize(
                800,
                500);
        dialog.setLocationRelativeTo(this);

        panel.getInputMap(
                        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_ESCAPE,
                                0),
                        "ESCAPE_KEY");

        panel.getActionMap()
                .put(
                        "ESCAPE_KEY",
                        new AbstractAction() {

                            @Override
                            public void actionPerformed(
                                    ActionEvent e) {

                                dialog.dispose();
                            }
                        });

        dialog.setVisible(true);
    }

    private void setFileTableLoading(boolean loading) {
        view.getFileTable().setEnabled(!loading);

        if (loading) {
            view.getFileTable().clearSelection();
            view.getFileTableModel().clearAndRepaint();
        }
    }

    private void goToParentFolder() {

        log.info("[PARENT NAV] ENTER");

        String currentPrefix =
                currentFilePrefix;

        if (currentPrefix == null
                || currentPrefix.isBlank()
                || S3TreeNode.ROOT_PREFIX.equals(currentPrefix)) {

            log.info(
                    "[PARENT NAV] already at root");

            return;
        }

        String parentPrefix =
                S3Util.extractParentPrefix(
                        currentPrefix);

        if (parentPrefix == null) {
            return;
        }

        /*
         * Parent klasöre döndüğümüzde,
         * az önce içinde bulunduğumuz klasörü
         * File Table'da seçmek istiyoruz.
         *
         * Örnek:
         *
         * current = SIL3/DOWNLOAD/
         * parent  = SIL3/
         *
         * parent File Table'da:
         *
         * DOWNLOAD/  <- selected
         */
        pendingFileTableSelectionKey =
                currentPrefix;

        restoreFileTableFocus =
                true;

        log.info(
                "[PARENT NAV] restore selection key={} parentPrefix={}",
                pendingFileTableSelectionKey,
                parentPrefix);

        log.info(
                "[PARENT NAV] navigating to parent={}",
                parentPrefix);

        treeController.selectPrefix(
                parentPrefix);
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
            RepositoryChangeEvent event) {

        if (event == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            RepositoryDefinition currentRepository =
                    getCurrentRepository();

            RepositoryDefinition repositoryToSelect =
                    currentRepository;

            switch (event.getType()) {

                case ADD -> {
                    /*
                     * Yeni repository eklendi.
                     *
                     * Mevcut repository korunur.
                     * Bucket / Tree / File Table reload edilmez.
                     */
                }

                case UPDATE -> {

                    RepositoryDefinition oldRepository =
                            event.getOldRepository();

                    RepositoryDefinition newRepository =
                            event.getNewRepository();

                    if (oldRepository == null
                            || newRepository == null) {
                        return;
                    }

                    boolean wasActive =
                            currentRepository != null
                                    && currentRepository != RepositoryDefinition.EMPTY_REPOSITORY
                                    && Objects.equals(
                                    currentRepository.getId(),
                                    oldRepository.getId());

                    if (wasActive) {

                        /*
                         * Aktif repository rename/update edildi.
                         *
                         * Yeni repository seçilecek fakat
                         * bucket/tree/file table reload edilmeyecek.
                         */
                        repositoryToSelect =
                                newRepository;

                        context.setActiveRepository(
                                newRepository);

                        encryptionConfig =
                                hasEncryptionConfiguration(
                                        newRepository)
                                        ? new EncryptionConfig(
                                        newRepository.getEncryptionTransformation(),
                                        newRepository.getEncryptionIv(),
                                        newRepository.getEncryptionKey())
                                        : null;

                    } else {

                        /*
                         * Aktif olmayan repository değiştirildi.
                         * Mevcut repository korunur.
                         */
                        repositoryToSelect =
                                currentRepository;
                    }
                }

                case REMOVE -> {

                    RepositoryDefinition removedRepository =
                            event.getOldRepository();

                    if (removedRepository == null) {
                        return;
                    }

                    boolean wasActive =
                            currentRepository != null
                                    && currentRepository != RepositoryDefinition.EMPTY_REPOSITORY
                                    && Objects.equals(
                                    currentRepository.getId(),
                                    removedRepository.getId());

                    if (wasActive) {

                        /*
                         * Aktif repository silindi.
                         *
                         * Empty Repository seçilecek.
                         * setSelectedRepository() bunun sonucunda
                         * bucket + tree + file table'ı temizleyecek.
                         */
                        repositoryToSelect =
                                RepositoryDefinition.EMPTY_REPOSITORY;

                    } else {

                        /*
                         * Aktif olmayan repository silindi.
                         * Mevcut repository korunur.
                         */
                        repositoryToSelect =
                                currentRepository;
                    }
                }
            }

            refreshRepositoryCombo(
                    repositoryToSelect);

            /*
             * Aktif repository silindiyse bütün Explorer state'ini
             * gerçekten temizle.
             *
             * Burada reloadBuckets() kullanılmıyor; çünkü o
             * File Table'ı temizlemiyor.
             */
            if (event.getType()
                    == RepositoryChangeEvent.Type.REMOVE
                    && repositoryToSelect
                    == RepositoryDefinition.EMPTY_REPOSITORY) {

                setSelectedRepository(
                        RepositoryDefinition.EMPTY_REPOSITORY);
            }
        });
    }

    public void setThreadCountSelectionListener(
            Consumer<Integer> listener) {

        view.setThreadCountSelectionListener(listener);
    }

    public void selectThreadCount(
            int threadCount) {
        view.selectThreadCount(threadCount);
    }

    private FileTableSortSpec getCurrentFileSortSpec() {

        if (!(view.getFileTable().getRowSorter()
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

        if (view.getFileTableRowLimitCombo() == null) {
            return 500;
        }

        Integer selected =
                (Integer)
                        view.getFileTableRowLimitCombo()
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
                                    .getId(),
                            bucket,
                            prefix
                                    + S3FileItem
                                    .PARENT_FOLDER_NAME,
                            0,
                            null,
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
                                                .getId(),
                                        bucket,
                                        folder,
                                        0,
                                        null,
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
                                                .getId(),
                                        bucket,
                                        object.key(),
                                        object.size(),
                                        object.lastModified(),
                                        object.storageClass() == null
                                                ? null
                                                : object.storageClass().toString(),
                                        false))
                        .toList());

        /*
         * JTable'a sonucu tek seferde veriyoruz.
         *
         * Lazy loading yok.
         */
        view.getFileTableModel().setFiles(rows);

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
                    S3Util.formatWithThousandSeparator(content.fileCount())
                            + " / "
                            + S3Util.formatWithThousandSeparator(content.scannedFileCount())
                            + " file(s)";

        } else {

            fileText =
                    S3Util.formatWithThousandSeparator(content.fileCount())
                            + " file(s)";
        }

        view.getFileFolderInfo().setText(
                S3Util.formatWithThousandSeparator(folderCount)
                        + " folder(s) and "
                        + fileText);
    }

    private void updateFileDiscoveryProgress(
            long generation,
            String bucket,
            String prefix,
            long fileCount,
            long folderCount) {

        SwingUtilities.invokeLater(() -> {

            if (generation !=
                    fileLoadGeneration.get()) {

                return;
            }

            view.getFileFolderInfo().setText(
                    "Preparing... "
                            + S3Util.formatWithThousandSeparator(folderCount)
                            + " folders / "
                            + S3Util.formatWithThousandSeparator(fileCount)
                            + " files discovered");

            if (fileTableDialog != null) {

                fileTableDialog.message.setText(
                        "<html>"
                                + "<b>Preparing file table...</b><br><br>"
                                + "<table>"
                                + "<tr><td><b>Bucket:</b></td><td>"
                                + bucket
                                + "</td></tr>"
                                + "<tr><td><b>Folder:</b></td><td>"
                                + (prefix == null || prefix.isBlank()
                                ? "/"
                                : prefix)
                                + "</td></tr>"
                                + "<tr><td><b>Folders found:</b></td><td>"
                                + S3Util.formatWithThousandSeparator(folderCount)
                                + "</td></tr>"
                                + "<tr><td><b>Files found:</b></td><td>"
                                + S3Util.formatWithThousandSeparator(fileCount)
                                + "</td></tr></table>"
                                + "</html>");

                fileTableDialog.dialog.pack();

                positionOperationDialogs();
            }
        });
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

        dialog.getRootPane()
                .getInputMap(
                        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_ESCAPE,
                                0),
                        "hideOperationDialog");

        dialog.getRootPane()
                .getActionMap()
                .put(
                        "hideOperationDialog",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(
                                    ActionEvent e) {

                                dialog.setVisible(false);
                            }
                        });

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

    private void restoreFileTableFocus() {

        restoreFileTableFocus(0);
    }

    private void restoreFileTableFocus(int attempt) {

        SwingUtilities.invokeLater(() -> {

            if (view.getFileTable().hasFocus()) {

                log.info(
                        "[FILE TABLE FOCUS RESTORE] success attempt={} focusOwner={} tableFocus=true",
                        attempt,
                        KeyboardFocusManager
                                .getCurrentKeyboardFocusManager()
                                .getFocusOwner());

                return;
            }

            boolean requested =
                    view.getFileTable().requestFocusInWindow();

            Component focusOwner =
                    KeyboardFocusManager
                            .getCurrentKeyboardFocusManager()
                            .getFocusOwner();

            log.debug(
                    "[FILE TABLE FOCUS RESTORE] attempt={} requested={} focusOwner={} tableFocus={}",
                    attempt,
                    requested,
                    focusOwner,
                    view.getFileTable().hasFocus());

            if (attempt < 5) {

                Timer retry =
                        new Timer(
                                40,
                                e -> restoreFileTableFocus(
                                        attempt + 1));

                retry.setRepeats(false);
                retry.start();
            }
        });
    }

    private void restoreFileTableSelectionByKey(
            String key) {

        if (key == null) {
            return;
        }

        JTable table =
                view.getFileTable();

        for (int viewRow = 0;
             viewRow < table.getRowCount();
             viewRow++) {

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            S3FileItem item =
                    view.getFileTableModel()
                            .getItem(modelRow);

            if (item == null) {
                continue;
            }

            if (Objects.equals(
                    item.getKey(),
                    key)) {

                table.setRowSelectionInterval(
                        viewRow,
                        viewRow);

                table.scrollRectToVisible(
                        table.getCellRect(
                                viewRow,
                                0,
                                true));

                updateActionStates();

                log.info(
                        "[FILE TABLE SELECTION RESTORE] key={} viewRow={} modelRow={}",
                        key,
                        viewRow,
                        modelRow);

                return;
            }
        }

        log.warn(
                "[FILE TABLE SELECTION RESTORE] key not found={}",
                key);
    }

    private List<String> getSelectedFileTableKeys() {

        JTable table =
                view.getFileTable();

        FileTableModel model =
                view.getFileTableModel();

        int[] selectedRows =
                table.getSelectedRows();

        if (selectedRows == null
                || selectedRows.length == 0) {

            return Collections.emptyList();
        }

        List<String> keys =
                new ArrayList<>();

        for (int viewRow : selectedRows) {

            if (viewRow < 0) {
                continue;
            }

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            S3FileItem item =
                    model.getItem(modelRow);

            if (item == null
                    || item.isParentFolder()) {
                continue;
            }

            keys.add(item.getKey());
        }

        return keys;
    }
    
    private void restoreFileTableSelectionByKeys(
            List<String> keys) {

        if (keys == null
                || keys.isEmpty()) {

            return;
        }

        JTable table =
                view.getFileTable();

        FileTableModel model =
                view.getFileTableModel();

        table.clearSelection();

        int firstViewRow = -1;
        int selectedCount = 0;

        for (int modelRow = 0;
             modelRow < model.getRowCount();
             modelRow++) {

            S3FileItem item =
                    model.getItem(modelRow);

            if (item == null) {
                continue;
            }

            if (!keys.contains(item.getKey())) {
                continue;
            }

            int viewRow =
                    table.convertRowIndexToView(
                            modelRow);

            if (viewRow < 0) {
                continue;
            }

            table.addRowSelectionInterval(
                    viewRow,
                    viewRow);

            if (firstViewRow < 0) {
                firstViewRow = viewRow;
            }

            selectedCount++;
        }

        if (firstViewRow >= 0) {

            table.scrollRectToVisible(
                    table.getCellRect(
                            firstViewRow,
                            0,
                            true));

            log.info(
                    "[FILE TABLE SELECTION RESTORE] multiple success selectedCount={} firstViewRow={} keys={}",
                    selectedCount,
                    firstViewRow,
                    keys);

        } else {

            log.warn(
                    "[FILE TABLE SELECTION RESTORE] multiple no matching rows keys={}",
                    keys);
        }
    }

    private boolean confirmFileConflict(
            S3FileItem item,
            String targetKey) {

        if (item == null
                || item.isFolder()
                || !exists(targetKey)) {

            return true;
        }

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        item.getName() + " already exists.\n"
                                + "Do you want to overwrite it?",
                        "File Conflict",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {

            log.info(
                    "[FILE CONFLICT] operation cancelled source={} target={}",
                    item.getKey(),
                    targetKey);

            return false;
        }

        log.info(
                "[FILE CONFLICT] overwrite confirmed source={} target={}",
                item.getKey(),
                targetKey);

        return true;
    }

    private boolean restorePendingPasteSelection() {

        if (pasteSelectionCollectionInProgress) {
            return false;
        }

        if (pendingFileTableSelectionKeys == null
                || pendingFileTableSelectionKeys.isEmpty()) {

            return false;
        }

        List<String> keys =
                new ArrayList<>(
                        pendingFileTableSelectionKeys);

        FileTableModel model =
                view.getFileTableModel();

        /*
         * IMPORTANT:
         *
         * A multi-file paste must not be considered complete
         * when only one of the pending items is present.
         *
         * Transfers complete asynchronously and the File Table
         * may therefore be refreshed while only a subset of the
         * accepted items has appeared.
         *
         * Wait until ALL pending keys are present in the table.
         */
        Set<String> availableKeys =
                new HashSet<>();

        for (int modelRow = 0;
             modelRow < model.getRowCount();
             modelRow++) {

            S3FileItem item =
                    model.getItem(modelRow);

            if (item == null) {
                continue;
            }

            availableKeys.add(
                    item.getKey());
        }

        if (!availableKeys.containsAll(keys)) {

            log.debug(
                    "[PASTE SELECTION] pending items not yet in table keys={} available={}",
                    keys,
                    availableKeys);

            return false;
        }

        /*
         * All accepted paste items are now present.
         *
         * Restore the complete selection in one operation.
         */
        restoreFileTableSelectionByKeys(keys);

        pendingFileTableSelectionKeys = null;

        /*
         * Selection has been restored successfully.
         *
         * Focus restoration is intentionally NOT performed here.
         * loadFiles() owns the focus restoration so that the
         * File Table receives exactly one focus-restore request
         * for the current refresh.
         */
        forceFileTableFocusAfterRefresh = false;

        restoreFileTableFocus = true;

        updateActionStates();

        log.info(
                "[PASTE SELECTION] restored and completed keys={}",
                keys);

        return true;
    }

    private String getOperationGroupName(
            List<S3FileItem> items) {

        if (items == null
                || items.isEmpty()) {
            return "";
        }

        String firstName =
                items.getFirst().getName();

        if (items.size() == 1) {
            return firstName;
        }

        return firstName
                + " and others";
    }

    private void showProperties() {

        if (view.getFileTable().getSelectedRowCount() != 1) {
            return;
        }

        S3FileItem item =
                getSelectedFileItem();

        if (item == null
                || item.isParentFolder()) {
            return;
        }

        PropertiesDialog dialog =
                new PropertiesDialog(
                        S3Util.getMainFrameAncestor(this),
                        item);

        if (item.isFolder()) {

            String bucket =
                    item.getBucket();

            String prefix =
                    item.getKey();

            explorerPool.submit(() -> {

                try {

                    FolderProperties properties =
                            getService().calculateFolderProperties(
                                    bucket,
                                    prefix,
                                    progress ->
                                            SwingUtilities.invokeLater(() ->
                                                    dialog.updateFolderProgress(
                                                            progress)));

                    SwingUtilities.invokeLater(() -> {

                        dialog.updateFolderProperties(
                                properties);

                        dialog.setCalculationCompleted();
                    });

                } catch (Exception ex) {

                    log.error(
                            "[PROPERTIES] folder calculation failed bucket={} prefix={}",
                            bucket,
                            prefix,
                            ex);

                    SwingUtilities.invokeLater(() ->
                            dialog.setStatus(
                                    "Calculation failed: "
                                            + S3ErrorResolver
                                            .getDetailedMessage(ex)));
                }
            });
        }

        dialog.setVisible(true);
    }

    private boolean hasEncryptionConfiguration() {
        RepositoryDefinition repository = getCurrentRepository();
        return this.hasEncryptionConfiguration(repository);
    }

    private boolean hasEncryptionConfiguration(
            RepositoryDefinition repository) {

        return repository != null
                && repository != RepositoryDefinition.EMPTY_REPOSITORY
                && repository.hasEncryptionConfiguration();
    }

    private void showBulkDownloadDialog() {
        RepositoryDefinition repository = getCurrentRepository();

        if (repository == null) {
            return;
        }

        String bucket = getCurrentBucket();

        if (bucket == null || bucket.isBlank()) {
            return;
        }

        BulkDownloadDialog dialog =
                new BulkDownloadDialog(
                        SwingUtilities.getWindowAncestor(this),
                        repository.getId(),
                        bucket,
                        hasEncryptionConfiguration());

        dialog.setVisible(true);

        if (dialog.getResult() == BulkDownloadDialog.Result.CANCEL) {
            return;
        }

        List<String> objectKeys =
                dialog.getObjectKeys();

        if (objectKeys.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please enter at least one S3 object key.");
            return;
        }

        JFileChooser chooser =
                new JFileChooser();

        chooser.setFileSelectionMode(
                JFileChooser.DIRECTORIES_ONLY);

        if (lastOpenedFolderToDownload != null) {
            chooser.setCurrentDirectory(
                    lastOpenedFolderToDownload);
        }

        int result =
                chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destinationFolder =
                chooser.getSelectedFile();

        lastOpenedFolderToDownload =
                destinationFolder;

        String repositoryId =
                repository.getId();

        if (dialog.getResult()
                == BulkDownloadDialog.Result.DOWNLOAD_DECRYPTED) {

            transferManager.submitBulkDownloadDecrypted(
                    repositoryId,
                    bucket,
                    objectKeys,
                    destinationFolder.toPath(),
                    encryptionConfig);

        } else {

            transferManager.submitBulkDownload(
                    repositoryId,
                    bucket,
                    objectKeys,
                    destinationFolder.toPath());
        }
    }

    public void updateFolderTreeRowHeight() {
        view.updateFolderTreeRowHeight();
    }

    public void updateFileTableRowHeight() {
        view.updateFileTableRowHeight();
    }

    private void addFolderToCurrentFileTable(
            String bucket,
            String folderKey) {

        if (bucket == null
                || folderKey == null
                || folderKey.isBlank()) {

            return;
        }

        if (!Objects.equals(
                currentFileBucket,
                bucket)) {

            return;
        }

        String parentPrefix =
                getParentPrefix(folderKey);

        if (!Objects.equals(
                currentFilePrefix,
                parentPrefix)) {

            return;
        }

        RepositoryDefinition repository =
                getCurrentRepository();

        if (repository == null) {
            return;
        }

        S3FileItem item =
                new S3FileItem(
                        repository.getId(),
                        bucket,
                        folderKey,
                        0L,
                        null,
                        null,
                        true);

        boolean added =
                view.getFileTableModel()
                        .addFile(item);

        if (!added) {

            log.debug(
                    "[FILE TABLE ROW INSERT] already exists key={}",
                    folderKey);

            return;
        }

        log.info(
                "[FILE TABLE ROW INSERT] key={} prefix={}",
                folderKey,
                currentFilePrefix);

        /*
         * CREATE_FOLDER işleminde oluşturulan klasörü
         * seç ve File Table focus'unu geri ver.
         */
        if (Objects.equals(
                pendingFileTableSelectionKey,
                folderKey)
                && restoreFileTableFocus) {

            SwingUtilities.invokeLater(() -> {

                int modelRow =
                        view.getFileTableModel()
                                .findRowByKey(folderKey);

                if (modelRow < 0) {
                    return;
                }

                JTable table =
                        view.getFileTable();

                int viewRow =
                        table.convertRowIndexToView(
                                modelRow);

                if (viewRow < 0) {
                    return;
                }

                table.setRowSelectionInterval(
                        viewRow,
                        viewRow);

                table.scrollRectToVisible(
                        table.getCellRect(
                                viewRow,
                                0,
                                true));

                table.requestFocusInWindow();

                log.info(
                        "[FILE TABLE SELECTION RESTORE] " +
                                "key={} viewRow={} modelRow={}",
                        folderKey,
                        viewRow,
                        modelRow);

                pendingFileTableSelectionKey = null;
                restoreFileTableFocus = false;
            });
        }
    }

    private void removeFolderFromCurrentFileTable(
            String bucket,
            String prefix) {

        if (bucket == null
                || prefix == null
                || prefix.isBlank()) {

            return;
        }

        if (!Objects.equals(
                currentFileBucket,
                bucket)) {

            return;
        }

        String parentPrefix =
                getParentPrefix(prefix);

        if (!Objects.equals(
                currentFilePrefix,
                parentPrefix)) {

            return;
        }

        boolean removed =
                view.getFileTableModel()
                        .removeFileByKey(prefix);

        log.info(
                "[FILE TABLE ROW REMOVE] key={} removed={}",
                prefix,
                removed);

        if (removed) {

            SwingUtilities.invokeLater(() -> {

                log.info(
                        "[DELETE SELECTION RESTORE TRIGGER] " +
                                "pendingRow={} rowCount={}",
                        pendingDeleteSelectionViewRow,
                        view.getFileTable().getRowCount());

                restoreFileTableSelectionAfterDelete();

                restoreFileTableFocus();
            });
        }
    }

    private void restoreFileTableSelectionAfterDelete() {

        JTable table =
                view.getFileTable();

        int rowCount =
                table.getRowCount();

        if (rowCount <= 0) {

            pendingDeleteSelectionViewRow = -1;
            return;
        }

        int targetViewRow =
                Math.min(
                        pendingDeleteSelectionViewRow,
                        rowCount - 1);

        if (targetViewRow < 0) {
            return;
        }

        table.setRowSelectionInterval(
                targetViewRow,
                targetViewRow);

        table.scrollRectToVisible(
                table.getCellRect(
                        targetViewRow,
                        0,
                        true));

        table.requestFocusInWindow();

        log.info(
                "[DELETE SELECTION RESTORE] " +
                        "deletedViewRow={} targetViewRow={} rowCount={}",
                pendingDeleteSelectionViewRow,
                targetViewRow,
                rowCount);

        pendingDeleteSelectionViewRow = -1;
    }

    private void addUploadedFileToCurrentFileTable(
            TransferTask task) {

        if (task == null) {
            return;
        }

        String bucket =
                task.getTargetBucket();

        String objectKey =
                task.getTargetObjectKey();

        if (bucket == null
                || objectKey == null
                || objectKey.isBlank()) {

            log.warn(
                    "[FILE TABLE UPLOAD INSERT] " +
                            "missing target bucket/key");
            return;
        }

        String parentPrefix =
                getParentPrefix(objectKey);

        /*
         * Upload edilen dosyanın parent klasörü
         * şu anda açık değilse File Table'a dokunma.
         */
        if (!Objects.equals(
                currentFileBucket,
                bucket)
                || !Objects.equals(
                currentFilePrefix,
                parentPrefix)) {

            log.debug(
                    "[FILE TABLE UPLOAD INSERT] " +
                            "skipped bucket={} prefix={} " +
                            "currentBucket={} currentPrefix={}",
                    bucket,
                    parentPrefix,
                    currentFileBucket,
                    currentFilePrefix);

            return;
        }

        RepositoryDefinition repository =
                getCurrentRepository();

        if (repository == null) {
            return;
        }

        /*
         * Upload task zaten local dosyanın boyutunu
         * taşıyor.
         */
        long size =
                task.getSize();

        Instant lastModified = null;

        if (task.getLocalPath() != null) {
            try {
                lastModified =
                        Files.getLastModifiedTime(
                                        task.getLocalPath())
                                .toInstant();

            } catch (Exception ex) {
                log.debug(
                        "[FILE TABLE UPLOAD INSERT] " +
                                "local lastModified could not be read " +
                                "key={}",
                        objectKey,
                        ex);
            }
        }

        S3FileItem item =
                new S3FileItem(
                        repository.getId(),
                        bucket,
                        objectKey,
                        size,
                        lastModified,
                        null,
                        false);

        boolean added =
                view.getFileTableModel()
                        .addFile(item);

        log.info(
                "[FILE TABLE UPLOAD INSERT] " +
                        "key={} size={} added={}",
                objectKey,
                size,
                added);

        if (!added) {
            return;
        }

        /*
         * JTable'ın mevcut TableRowSorter'ı
         * model event'ini aldıktan sonra yeni satırı
         * seçili kolonun sıralamasına göre
         * yeniden konumlandıracaktır.
         */
        if (Objects.equals(
                pendingFileTableSelectionKey,
                objectKey)
                && restoreFileTableFocus) {

            SwingUtilities.invokeLater(() -> {

                restoreFileTableSelectionByKey(
                        objectKey);

                restoreFileTableFocus();

                pendingFileTableSelectionKey =
                        null;

                restoreFileTableFocus =
                        false;
            });
        }
    }

    private void addCopiedFileToCurrentTable(
            TransferTask task) {

        if (task == null) {
            return;
        }

        String bucket =
                task.getTargetBucket();

        String objectKey =
                task.getTargetObjectKey();

        if (bucket == null
                || objectKey == null
                || objectKey.isBlank()) {

            log.warn(
                    "[FILE TABLE COPY INSERT] " +
                            "missing target bucket/key");
            return;
        }

        String parentPrefix =
                getParentPrefix(objectKey);

        if (!Objects.equals(
                currentFileBucket,
                bucket)
                || !Objects.equals(
                currentFilePrefix,
                parentPrefix)) {

            log.debug(
                    "[FILE TABLE COPY INSERT] skipped " +
                            "bucket={} prefix={} " +
                            "currentBucket={} currentPrefix={}",
                    bucket,
                    parentPrefix,
                    currentFileBucket,
                    currentFilePrefix);

            return;
        }

        S3FileItem item =
                new S3FileItem(
                        task.getTargetRepositoryId(),
                        bucket,
                        objectKey,
                        task.getSize(),
                        null,
                        null,
                        false);

        boolean added =
                view.getFileTableModel()
                        .addFile(item);

        log.info(
                "[FILE TABLE COPY INSERT] " +
                        "source={}/{} target={}/{} " +
                        "size={} added={}",
                task.getBucket(),
                task.getObjectKey(),
                bucket,
                objectKey,
                task.getSize(),
                added);

        if (!added) {
            return;
        }

        /*
         * Paste sırasında File Table reload edilmiyor.
         *
         * Bu nedenle pendingFileTableSelectionKeys burada
         * incremental INSERT sonrasında doğrudan restore edilmelidir.
         */
        if (pendingFileTableSelectionKeys != null
                && !pendingFileTableSelectionKeys.isEmpty()
                && !pasteSelectionCollectionInProgress) {

            SwingUtilities.invokeLater(() -> {

                if (restorePendingPasteSelection()) {

                    restoreFileTableFocus();

                    log.info(
                            "[FILE TABLE COPY SELECTION] " +
                                    "paste selection restored incrementally key={}",
                            objectKey);
                }
            });

            return;
        }

        /*
         * Upload / tekil COPY gibi eski tek-key selection
         * mekanizmasını koru.
         */
        if (Objects.equals(
                pendingFileTableSelectionKey,
                objectKey)
                && restoreFileTableFocus) {

            SwingUtilities.invokeLater(() -> {

                restoreFileTableSelectionByKey(
                        objectKey);

                restoreFileTableFocus();

                pendingFileTableSelectionKey =
                        null;

                restoreFileTableFocus =
                        false;
            });
        }
    }

    public void openRepositoryManager() {
        showRepositoryManager();
    }

    private void refreshRepositoryCombo(
            RepositoryDefinition repositoryToSelect) {

        List<RepositoryDefinition> repositories =
                repositoryManager.getRepositories();

        Collator turkishCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkishCollator.setStrength(
                Collator.PRIMARY);

        repositories.sort(
                (first, second) ->
                        S3Util.naturalTurkishCompare(
                                first.getId(),
                                second.getId(),
                                turkishCollator));

        suppressRepositorySelectionEvent = true;

        try {

            DefaultComboBoxModel<RepositoryDefinition> model =
                    (DefaultComboBoxModel<RepositoryDefinition>)
                            view.getRepositoryCombo()
                                    .getModel();

            model.removeAllElements();

            model.addElement(
                    RepositoryDefinition.EMPTY_REPOSITORY);

            for (RepositoryDefinition repository :
                    repositories) {

                model.addElement(repository);
            }

            RepositoryDefinition selectedRepository =
                    RepositoryDefinition.EMPTY_REPOSITORY;

            if (repositoryToSelect != null
                    && repositoryToSelect != RepositoryDefinition.EMPTY_REPOSITORY) {

                for (int i = 1;
                     i < model.getSize();
                     i++) {

                    RepositoryDefinition repository =
                            model.getElementAt(i);

                    if (repository != null
                            && Objects.equals(
                            repository.getId(),
                            repositoryToSelect.getId())) {

                        selectedRepository =
                                repository;

                        break;
                    }
                }
            }

            model.setSelectedItem(
                    selectedRepository);

        } finally {

            suppressRepositorySelectionEvent = false;
        }
    }
}
