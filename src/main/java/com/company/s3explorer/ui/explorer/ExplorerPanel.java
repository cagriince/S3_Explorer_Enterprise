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
import com.company.s3explorer.util.SizeFormatter;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ExplorerPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ExplorerPanel.class);

    private static final int OPERATION_DIALOG_DELAY_MS = 250;
    private static final DateTimeFormatter EXPORT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
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
    private final AtomicLong fileDiscoveryGeneration = new AtomicLong();
    private final AtomicLong operationGeneration = new AtomicLong();
    private String currentFileBucket;
    private String currentFilePrefix;
    private final AtomicLong rootFolderLoadGeneration = new AtomicLong();

    private OperationDialog bucketDialog;
    private OperationDialog fileTableDialog;
    private OperationDialog filterRefreshDialog;

    private enum OperationDialogType {
        BUCKET,
        FILE_TABLE,
        FILTER_REFRESH
    }

    private final List<OperationDialog> visibleOperationDialogs = new ArrayList<>();

    private File lastOpenedFolderToUpload;
    private File lastOpenedFolderToDownload;
    private File lastOpenedFolderToExport;

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
    private Consumer<Boolean> folderEditModeSelectionListener;
    private RepositoryDefinition pendingRepositorySelection;
    private String pendingBucketSelection;
    private boolean suppressBucketSelectionEvent;
    private boolean suppressRepositorySelectionEvent;
    private boolean forceBucketReload;
    private boolean currentFileTableLimitReached;

    private String lastAppliedFilterText = "";
    private String lastAppliedItemType = "All";

    private record NavigationEntry(
            String repositoryId,
            String bucket,
            String prefix) {
    }
    private final List<NavigationEntry> navigationHistory = new ArrayList<>();
    private int navigationHistoryIndex = -1;
    /*
     * Geri/ileri veya konum alanından başlatılan geçişin
     * tamamlanmasını bekleyen hedef.
     */
    private NavigationEntry pendingNavigationTarget;
    private boolean navigationPlayback;
    /*
     * Geçmiş navigasyonu başarısız olursa
     * kullanıcının bulunduğu eski konumu geri yüklemek için.
     */
    private int pendingNavigationPreviousIndex = -1;

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
    private Action backAction;
    private Action forwardAction;
    private Action reloadFolderAction;
    private Action flatBrowsingAction;
    private boolean flatBrowsingEnabled;
    private Action folderEditAction;
    private Action goToParentAction;
    private Action renameAction;
    private Action propertiesAction;
    private Action bulkDownloadAction;
    private Action selectAllAction;
    private Action homeAction;
    private Action clearHistoryAction;
    private Action exportFileListAction;

    private JMenuItem uploadEncryptedMenuItem;
    private JMenuItem downloadDecryptedMenuItem;

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
                backAction,
                forwardAction,
                reloadFolderAction,
                flatBrowsingAction,
                folderEditAction,
                exportFileListAction,
                node -> treeController.loadChildren(node),
                this::openSelectedFileItem,
                this::reloadCurrentFileTableAfterSort,
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
                        this::updateActionStates,
                        repositoryManager::findById);

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

                    reloadCurrentFileTable(true);
                };

        view.setFileTableRowLimitSelectionListener(
                fileTableRowLimitSelectionListener);
        lastAppliedFilterText = view.getFilterText();
        lastAppliedItemType = view.getItemTypeFilter();
        view.setServerFilterApplyListener(
                this::applyServerFiltersIfChanged);

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

        selectAllAction.putValue(
                Action.SMALL_ICON,
                IconProvider.ICON_CHECK_ALL);

        goToParentAction.putValue(
                Action.SMALL_ICON,
                IconProvider.ICON_PARENT_FOLDER);

        homeAction.putValue(
                Action.SMALL_ICON,
                IconProvider.ICON_HOME);

        clearHistoryAction.putValue(
                Action.SMALL_ICON,
                IconProvider.ICON_CLEAR_HISTORY);

        exportFileListAction.putValue(
                Action.SMALL_ICON,
                IconProvider.ICON_TABLE_EXPORT);
    }

    private void createActions() {
        manageRepositoryAction = new ExplorerAction("Repository Manager", this::showRepositoryManager);
        refreshAction = new ExplorerAction("Reload Bucket", this::loadBucketsAsync);
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
        goToParentAction = new ExplorerAction("Up", this::goToParentFolder);
        selectAllAction = new ExplorerAction("Select All", this::selectAllFiles);
        homeAction = new ExplorerAction("Home", this::goHome);
        clearHistoryAction = new ExplorerAction("Clear History", this::clearNavigationHistory);
        renameAction = new ExplorerAction("Rename", () -> renameController.renameSelected());
        propertiesAction = new ExplorerAction("Properties", this::showProperties);
        bulkDownloadAction = new ExplorerAction("Bulk Download", this::showBulkDownloadDialog);
        backAction = new ExplorerAction("Back", this::goBack);
        forwardAction = new ExplorerAction("Forward", this::goForward);
        flatBrowsingAction = new ExplorerAction("Flat Browsing", this::toggleFlatBrowsing);
        folderEditAction = new ExplorerAction("Folder Edit", this::changeFolderEditStatus);
        reloadFolderAction = new ExplorerAction("Reload Folder", this::reloadCurrentFolder);

        exportFileListAction = new ExplorerAction("Export List", this::exportFileList);
        exportFileListAction.setEnabled(false);
    }

    private void defineShortCuts() {
        // -------------------------------------------------
        // File Table
        // -------------------------------------------------

        InputMap inputMap = view.getFileTable().getInputMap(
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
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

        inputMap.put(KeyStroke.getKeyStroke("control V"), "paste");
        actionMap.put("paste", pasteAction);

        // Backspace
        inputMap.put(
                KeyStroke.getKeyStroke("BACK_SPACE"),
                "explorerGoParent");
        actionMap.put("explorerGoParent", goToParentAction);

        // Delete
        inputMap.put(
                KeyStroke.getKeyStroke("DELETE"),
                "deleteSelected");
        actionMap.put("deleteSelected", deleteAction);

        // Rename
        inputMap.put(
                KeyStroke.getKeyStroke("F2"),
                "renameSelected");
        actionMap.put("renameSelected", renameAction);

        // Properties
        inputMap.put(
                KeyStroke.getKeyStroke("alt ENTER"),
                "showProperties");
        actionMap.put("showProperties", propertiesAction);

        // Select all files when the file table has focus.
        inputMap.put(
                KeyStroke.getKeyStroke("control A"),
                "selectAllFiles");
        actionMap.put("selectAllFiles", selectAllAction);

        // -------------------------------------------------
        // Tree
        // -------------------------------------------------

        InputMap treeInputMap = view.getFolderTree().getInputMap(
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap treeActionMap = view.getFolderTree().getActionMap();

        treeInputMap.put(
                KeyStroke.getKeyStroke("BACK_SPACE"),
                "explorerGoParent");
        treeActionMap.put("explorerGoParent", goToParentAction);

        // -------------------------------------------------
        // ExplorerPanel
        // -------------------------------------------------

        InputMap panelInputMap =
                getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap panelActionMap = getActionMap();

        // Ctrl+V
        panelInputMap.put(
                KeyStroke.getKeyStroke("control V"),
                "explorerPaste");
        panelActionMap.put("explorerPaste", pasteAction);

        // Backspace
        panelInputMap.put(
                KeyStroke.getKeyStroke("BACK_SPACE"),
                "explorerGoParent");
        panelActionMap.put("explorerGoParent", goToParentAction);

        // Alt + Left: Back
        panelInputMap.put(
                KeyStroke.getKeyStroke("alt LEFT"),
                "explorerBack");
        panelActionMap.put("explorerBack", backAction);

        // Alt + Right: Forward
        panelInputMap.put(
                KeyStroke.getKeyStroke("alt RIGHT"),
                "explorerForward");
        panelActionMap.put("explorerForward", forwardAction);

        // F5: Reload Folder
        panelInputMap.put(
                KeyStroke.getKeyStroke("F5"),
                "reloadFolder");
        panelActionMap.put("reloadFolder", reloadFolderAction);

        // Ctrl+A when the blank area of the file table viewport
        // has focus. Do not interfere with text-field selection.
        panelInputMap.put(
                KeyStroke.getKeyStroke("control A"),
                "explorerSelectAllFiles");

        panelActionMap.put(
                "explorerSelectAllFiles",
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Component focusOwner =
                                KeyboardFocusManager
                                        .getCurrentKeyboardFocusManager()
                                        .getFocusOwner();

                        boolean fileTableFocused =
                                focusOwner == view.getFileTable();

                        boolean fileTableViewportFocused =
                                focusOwner instanceof JViewport viewport
                                        && viewport.getView()
                                        == view.getFileTable();

                        if (fileTableFocused || fileTableViewportFocused) {
                            selectAllFiles();
                        }
                    }
                });

        // Location field shortcuts
        InputMap locationInputMap =
                view.getLocationField().getInputMap(
                        JComponent.WHEN_FOCUSED);
        ActionMap locationActionMap =
                view.getLocationField().getActionMap();

        locationInputMap.put(
                KeyStroke.getKeyStroke("alt LEFT"),
                "explorerBack");
        locationActionMap.put("explorerBack", backAction);

        locationInputMap.put(
                KeyStroke.getKeyStroke("alt RIGHT"),
                "explorerForward");
        locationActionMap.put("explorerForward", forwardAction);
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
        updateSelectedFileInfo();

        boolean folderSelected =
                currentFileBucket != null
                        && currentFilePrefix != null;

        JTable table =
                view.getFileTable();

        int selectedRowCount =
                table.getSelectedRowCount();

        boolean hasSelection = false;

        if (folderSelected && selectedRowCount > 0) {
            if (selectedRowCount > 1) {
                hasSelection = true;
            } else {
                S3FileItem item = getSelectedFileItem();

                hasSelection =
                        item != null
                                && !item.isParentFolder();
            }
        }

        boolean hasClipboard =
                folderSelected
                        && !clipboard.isEmpty();

        newFolderAction.setEnabled(folderSelected);
        uploadAction.setEnabled(folderSelected);

        uploadEncryptedAction.setEnabled(
                folderSelected
                        && hasEncryptionConfiguration());

        downloadAction.setEnabled(hasSelection);

        downloadDecryptedAction.setEnabled(
                hasSelection
                        && hasEncryptionConfiguration());

        deleteAction.setEnabled(hasSelection);
        renameAction.setEnabled(hasSelection);

        List<S3FileItem> selectedItems =
                getSelectedFileItems();

        propertiesAction.setEnabled(
                selectedRowCount > 0
                        && selectedItems.size() == selectedRowCount);

        copyAction.setEnabled(hasSelection);
        cutAction.setEnabled(hasSelection);
        pasteAction.setEnabled(hasClipboard);
        copyTextAction.setEnabled(hasSelection);

        reloadFolderAction.setEnabled(folderSelected);
        exportFileListAction.setEnabled(folderSelected);
        bulkDownloadAction.setEnabled(hasSelection);

        selectAllAction.setEnabled(
                folderSelected
                        && table.getRowCount() > 0);

        boolean atRoot =
                currentFilePrefix == null
                        || currentFilePrefix.isBlank()
                        || S3TreeNode.ROOT_PREFIX.equals(currentFilePrefix);

        goToParentAction.setEnabled(
                folderSelected
                        && !atRoot);

        homeAction.setEnabled(
                folderSelected
                        && !atRoot);

        refreshAction.setEnabled(
                getCurrentRepository() != null
                        && !getCurrentRepository().isEmpty());

        clearHistoryAction.setEnabled(
                !navigationHistory.isEmpty()
                        && pendingNavigationTarget == null);

        updateEncryptionMenuVisibility();

        log.debug(
                "[ACTION STATES] folderSelected={} selectedRows={} "
                        + "hasSelection={} hasClipboard={}",
                folderSelected,
                selectedRowCount,
                hasSelection,
                hasClipboard);
    }

    public void updatePopupUI() {
        if (view != null) {
            view.updatePopupUI();
        }
    }

    public void loadRepositoriesAsync() {

        explorerPool.submit(() -> {

            try {

                List<RepositoryDefinition> repositories =
                        repositoryManager.getRepositories();

                SwingUtilities.invokeLater(() -> {

                    /*
                     * Repository listesi oluşturulurken ComboBox'ın
                     * otomatik selection event'leri Explorer'ın
                     * repository değiştirme akışını tetiklememeli.
                     *
                     * Aksi halde ilk repository addItem() sırasında
                     * otomatik olarak seçilip:
                     *
                     *     setSelectedRepository()
                     *          ->
                     *     "Connecting to S3 repository..."
                     *
                     * zinciri oluşuyor.
                     */
                    suppressRepositorySelectionEvent = true;

                    try {

                        view.getRepositoryCombo()
                                .removeAllItems();

                        view.getRepositoryCombo()
                                .addItem(
                                        RepositoryDefinition.EMPTY_REPOSITORY);

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

                        for (RepositoryDefinition repository :
                                repositories) {

                            view.getRepositoryCombo()
                                    .addItem(repository);
                        }

                        /*
                         * -------------------------------------------------
                         * INITIAL SELECTION
                         * -------------------------------------------------
                         *
                         * ComboBox artık tamamen dolduruldu.
                         *
                         * Bundan sonra hangi repository'nin seçileceğine
                         * karar veriyoruz.
                         */
                        RepositoryDefinition repositoryToSelect =
                                pendingRepositorySelection;

                        pendingRepositorySelection = null;

                        if (repositoryToSelect == null) {

                            repositoryToSelect =
                                    RepositoryDefinition.EMPTY_REPOSITORY;
                        }

                        /*
                         * Model içinde gerçekten mevcut olan repository
                         * instance'ını bul.
                         *
                         * Özellikle RepositoryDefinition.equals()
                         * implementasyonundan bağımsız olarak ID üzerinden
                         * eşleştiriyoruz.
                         */
                        RepositoryDefinition actualRepository =
                                RepositoryDefinition.EMPTY_REPOSITORY;

                        if (repositoryToSelect != null
                                && !repositoryToSelect.isEmpty()) {

                            for (int i = 0;
                                 i < view.getRepositoryCombo()
                                         .getItemCount();
                                 i++) {

                                RepositoryDefinition repository =
                                        view.getRepositoryCombo()
                                                .getItemAt(i);

                                if (repository != null
                                        && !repository.isEmpty()
                                        && Objects.equals(
                                        repository.getId(),
                                        repositoryToSelect.getId())) {

                                    actualRepository =
                                            repository;

                                    break;
                                }
                            }
                        }

                        /*
                         * Selection event'i hâlâ bastırılmış durumda.
                         *
                         * Dolayısıyla burada yalnızca ComboBox selection
                         * yapılır; repository bağlantısı başlamaz.
                         */
                        view.getRepositoryCombo()
                                .setSelectedItem(
                                        actualRepository);

                    } finally {

                        suppressRepositorySelectionEvent = false;
                    }

                    /*
                     * -------------------------------------------------
                     * INITIAL REPOSITORY ACTIVATION
                     * -------------------------------------------------
                     *
                     * ComboBox tamamen hazırlandıktan ve suppression
                     * kaldırılmadan önce selection belirlendi.
                     *
                     * Burada yalnızca gerçek bir repository seçilmişse
                     * bağlantıyı başlatıyoruz.
                     *
                     * Empty repository seçildiyse:
                     *
                     *     setSelectedRepository(EMPTY)
                     *
                     * çağrılmıyor.
                     *
                     * Böylece uygulama ilk açılışta boş kalıyorsa
                     * kesinlikle S3 bağlantısı denenmiyor.
                     */
                    RepositoryDefinition selectedRepository =
                            (RepositoryDefinition)
                                    view.getRepositoryCombo()
                                            .getSelectedItem();

                    if (selectedRepository == null
                            || selectedRepository.isEmpty()) {

                        return;
                    }

                    setSelectedRepository(
                            selectedRepository);
                });

            } catch (Exception ex) {

                log.error(
                        "Explorer operation failed",
                        ex);

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
                        NavigationEntry restoreTarget = pendingNavigationTarget;

                        boolean restoreThisRepository =
                                navigationPlayback
                                        && restoreTarget != null
                                        && Objects.equals(
                                        restoreTarget.repositoryId(),
                                        repository.getId());

                        String desiredBucket = restoreThisRepository
                                ? restoreTarget.bucket()
                                : previousBucket;

                        if (desiredBucket != null
                                && allBuckets.contains(desiredBucket)) {

                            view.getBucketCombo().setSelectedItem(desiredBucket);

                        } else if (view.getBucketCombo().getItemCount() > 0) {

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

        if (bucket == null || bucket.isBlank()) {
            return;
        }

        /*
         * Loading a different bucket or returning to the bucket root
         * changes the active file-table location.
         */
        if (!Objects.equals(bucket, currentFileBucket)
                || !Objects.equals(
                S3TreeNode.ROOT_PREFIX,
                currentFilePrefix)) {

            prepareForLocationChange();
        }

        /*
         * Keep operationGeneration separate: it also protects the
         * asynchronous repository/bucket-list loading flow.
         */
        final long operationId =
                operationGeneration.get();

        /*
         * A newer root load supersedes every older root load.
         */
        final long rootLoadId =
                rootFolderLoadGeneration.incrementAndGet();

        final String prefix =
                S3TreeNode.ROOT_PREFIX;

        final int fileLimit =
                getSelectedFileTableRowLimit();

        final FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        final boolean flatBrowsing =
                flatBrowsingEnabled;

        final FileTableFilter filter =
                getCurrentFileTableFilter();

        contentLoader.loadFolder(
                        explorerPool,
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec,
                        null,
                        flatBrowsing,
                        filter)
                .thenAccept(content ->
                        SwingUtilities.invokeLater(() -> {

                            if (operationId
                                    != operationGeneration.get()
                                    || rootLoadId
                                    != rootFolderLoadGeneration.get()) {
                                return;
                            }

                            treeController.applyRootFolders(
                                    bucket,
                                    content.folders());

                            hideOperationDialog(
                                    OperationDialogType.BUCKET);

                            loadFiles(
                                    bucket,
                                    prefix,
                                    content);

                            updateBreadcrumb(prefix);

                            NavigationEntry restoreTarget =
                                    pendingNavigationTarget;

                            if (navigationPlayback
                                    && restoreTarget != null
                                    && Objects.equals(
                                    restoreTarget.repositoryId(),
                                    getCurrentRepository() == null
                                            ? null
                                            : getCurrentRepository().getId())
                                    && Objects.equals(
                                    restoreTarget.bucket(),
                                    bucket)) {

                                restorePendingNavigationPrefix(
                                        restoreTarget);

                            } else {
                                recordNavigation();
                            }

                            updateNavigationButtonStates();
                            updateActionStates();
                        }))
                .exceptionally(ex -> {

                    log.error(
                            "[FOLDER LOAD] failed: {}",
                            S3ErrorResolver.getDetailedMessage(ex));

                    SwingUtilities.invokeLater(() -> {

                        if (operationId
                                != operationGeneration.get()
                                || rootLoadId
                                != rootFolderLoadGeneration.get()) {
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

        final String repositoryId =
                getCurrentRepository().getId();

        final boolean flatBrowsing =
                flatBrowsingEnabled;

        CompletableFuture
                .supplyAsync(() -> {
                    long prepareStart = System.nanoTime();

                    List<S3FileItem> rows =
                            createFileTableRows(
                                    repositoryId,
                                    bucket,
                                    prefix,
                                    content,
                                    flatBrowsing);

                    long prepareMillis =
                            (System.nanoTime() - prepareStart) / 1_000_000;

                    log.info(
                            "[FILE TABLE TIMING] row preparation: {} ms, rows={}",
                            prepareMillis,
                            rows.size());

                    return new PreparedFileTable(
                            content,
                            rows);
                }, explorerPool)
                .thenAccept(prepared ->
                        SwingUtilities.invokeLater(() -> {

                            if (generation != fileLoadGeneration.get()) {
                                return;
                            }

                            long applyStart = System.nanoTime();

                            applyPreparedFileTableRows(
                                    prepared.rows(),
                                    prepared.content(),
                                    bucket,
                                    prefix);

                            long applyMillis =
                                    (System.nanoTime() - applyStart) / 1_000_000;

                            log.info(
                                    "[FILE TABLE TIMING] model apply: {} ms, rows={}",
                                    applyMillis,
                                    prepared.rows().size());

                            setFileTableLoading(false);

                            hideOperationDialog(
                                    OperationDialogType.FILE_TABLE);

                            hideOperationDialog(
                                    OperationDialogType.FILTER_REFRESH);
                        }))
                .exceptionally(ex -> {

                    log.error(
                            "[FILE LOAD] failed: {}",
                            S3ErrorResolver.getDetailedMessage(ex));

                    SwingUtilities.invokeLater(() -> {

                        if (generation != fileLoadGeneration.get()) {
                            return;
                        }

                        setFileTableLoading(false);

                        hideOperationDialog(
                                OperationDialogType.FILE_TABLE);

                        hideOperationDialog(
                                OperationDialogType.FILTER_REFRESH);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver.getUserMessage(ex),
                                "S3 Operation Failed",
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
                false,
                false);
    }

    private void loadFiles(
            String bucket,
            String prefix,
            boolean restoreFocus) {

        loadFiles(
                bucket,
                prefix,
                restoreFocus,
                false);
    }



    private void loadFiles(
            String bucket,
            String prefix,
            boolean restoreFocus,
            boolean filterRefresh) {

        // Capture the current location before updating it.
        final boolean sameLocation =
                Objects.equals(currentFileBucket, bucket)
                        && Objects.equals(currentFilePrefix, prefix);

        final boolean flatBrowsing =
                flatBrowsingEnabled;

        final boolean cachedSnapshotAvailable =
                contentLoader.getCachedSnapshot(
                        bucket,
                        prefix,
                        flatBrowsing) != null;

        final boolean discoveryInProgress =
                contentLoader.isDiscoveryInProgress(
                        bucket,
                        prefix,
                        flatBrowsing);

        final boolean preparingDialogActive =
                sameLocation
                        && fileTableDialog != null
                        && visibleOperationDialogs.contains(fileTableDialog);

        final long generation =
                fileLoadGeneration.incrementAndGet();

        final long discoveryGeneration =
                fileDiscoveryGeneration.get();

        currentFileBucket = bucket;
        currentFilePrefix = prefix;

        final int fileLimit =
                getSelectedFileTableRowLimit();

        final FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        final String repositoryId =
                getCurrentRepository().getId();

        final var filter =
                getCurrentFileTableFilter();

        log.info(
                "[FILE LOAD TRACE] START generation={}, bucket={}, prefix={}, "
                        + "sortColumn={}, ascending={}, fileLimit={}, "
                        + "cachedSnapshot={}, discoveryInProgress={}, filterRefresh={}",
                generation,
                bucket,
                prefix,
                sortSpec.getColumn(),
                sortSpec.isAscending(),
                fileLimit,
                cachedSnapshotAvailable,
                discoveryInProgress,
                filterRefresh);

        setFileTableLoading(true);

        /*
         * Keep the existing Preparing dialog while discovery is in progress.
         * Never replace its live counters with a second dialog.
         */
        if (preparingDialogActive) {

            // Keep the existing dialog and its progress counters.

        } else if (discoveryInProgress) {

            hideOperationDialog(
                    OperationDialogType.FILTER_REFRESH);

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

        } else if (cachedSnapshotAvailable) {

            // The data has already been discovered; only the display
            // needs to be recalculated.
            hideOperationDialog(
                    OperationDialogType.FILE_TABLE);

            String message = filterRefresh
                    ? "<html><b>Applying filters...</b><br>"
                    + "Please wait while the file table is updated."
                    + "</html>"
                    : "<html><b>Updating file table...</b><br>"
                    + "Please wait while the file table is updated."
                    + "</html>";

            showOperationDialog(
                    OperationDialogType.FILTER_REFRESH,
                    message);

        } else {

            hideOperationDialog(
                    OperationDialogType.FILTER_REFRESH);

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

            updateFileDiscoveryProgress(
                    discoveryGeneration,
                    bucket,
                    prefix,
                    0,
                    0);
        }

        contentLoader.loadFolder(
                        explorerPool,
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec,
                        (fileCount, folderCount) ->
                                updateFileDiscoveryProgress(
                                        discoveryGeneration,
                                        bucket,
                                        prefix,
                                        fileCount,
                                        folderCount),
                        flatBrowsing,
                        filter)
                .thenApplyAsync(content -> {

                    /*
                     * The folder discovery/display sorting has completed.
                     * If a newer request exists, skip the additional row
                     * preparation for this obsolete request.
                     */
                    if (generation != fileLoadGeneration.get()) {

                        log.info(
                                "[FILE LOAD TRACE] STALE_AFTER_CONTENT "
                                        + "generation={}, currentGeneration={}, "
                                        + "bucket={}, prefix={}, sortColumn={}, ascending={}",
                                generation,
                                fileLoadGeneration.get(),
                                bucket,
                                prefix,
                                sortSpec.getColumn(),
                                sortSpec.isAscending());

                        return null;
                    }

                    long prepareStart =
                            System.nanoTime();

                    List<S3FileItem> rows =
                            createFileTableRows(
                                    repositoryId,
                                    bucket,
                                    prefix,
                                    content,
                                    flatBrowsing);

                    long prepareMillis =
                            (System.nanoTime() - prepareStart) / 1_000_000;

                    log.info(
                            "[FILE TABLE TIMING] generation={}, "
                                    + "row preparation: {} ms, rows={}",
                            generation,
                            prepareMillis,
                            rows.size());

                    return new PreparedFileTable(
                            content,
                            rows);

                }, explorerPool)
                .thenAccept(prepared -> {

                    // A stale request deliberately returns null above.
                    if (prepared == null) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {

                        if (generation != fileLoadGeneration.get()) {

                            log.info(
                                    "[FILE LOAD TRACE] STALE_BEFORE_APPLY "
                                            + "generation={}, currentGeneration={}, "
                                            + "bucket={}, prefix={}",
                                    generation,
                                    fileLoadGeneration.get(),
                                    bucket,
                                    prefix);

                            return;
                        }

                        long applyStart =
                                System.nanoTime();

                        applyPreparedFileTableRows(
                                prepared.rows(),
                                prepared.content(),
                                bucket,
                                prefix);

                        long applyMillis =
                                (System.nanoTime() - applyStart) / 1_000_000;

                        log.info(
                                "[FILE TABLE TIMING] generation={}, "
                                        + "model apply: {} ms, rows={}",
                                generation,
                                applyMillis,
                                prepared.rows().size());

                        log.info(
                                "[FILE LOAD TRACE] APPLIED generation={}, "
                                        + "bucket={}, prefix={}, rows={}",
                                generation,
                                bucket,
                                prefix,
                                prepared.rows().size());

                        setFileTableLoading(false);

                        hideOperationDialog(
                                OperationDialogType.FILE_TABLE);

                        hideOperationDialog(
                                OperationDialogType.FILTER_REFRESH);

                        if (!pasteSelectionCollectionInProgress) {

                            if (pendingFileTableSelectionKeys != null
                                    && !pendingFileTableSelectionKeys.isEmpty()) {

                                restorePendingPasteSelection();

                            } else if (pendingFileTableSelectionKey != null) {

                                restoreFileTableSelectionByKey(
                                        pendingFileTableSelectionKey);

                                pendingFileTableSelectionKey = null;

                            } else if (preservedFileTableSelectionKeys != null
                                    && !preservedFileTableSelectionKeys.isEmpty()) {

                                List<String> preservedKeys =
                                        new ArrayList<>(
                                                preservedFileTableSelectionKeys);

                                restoreFileTableSelectionByKeys(
                                        preservedKeys);

                                preservedFileTableSelectionKeys = null;

                                log.debug(
                                        "[FILE TABLE SELECTION] restored preserved selection keys={}",
                                        preservedKeys);
                            }
                        }

                        if (pendingDeleteSelectionViewRow >= 0) {

                            restoreFileTableSelectionAfterDelete();

                            pendingDeleteSelectionViewRow = -1;
                        }

                        if (restoreFocus || restoreFileTableFocus) {

                            restoreFileTableFocus();

                            restoreFileTableFocus = false;
                        }
                    });
                })
                .exceptionally(ex -> {

                    log.error(
                            "[FILE LOAD TRACE] FAILED generation={}, "
                                    + "bucket={}, prefix={}, error={}",
                            generation,
                            bucket,
                            prefix,
                            S3ErrorResolver.getDetailedMessage(ex));

                    SwingUtilities.invokeLater(() -> {

                        if (generation != fileLoadGeneration.get()) {
                            return;
                        }

                        setFileTableLoading(false);

                        hideOperationDialog(
                                OperationDialogType.FILE_TABLE);

                        hideOperationDialog(
                                OperationDialogType.FILTER_REFRESH);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver.getUserMessage(ex),
                                "S3 Operation Failed",
                                JOptionPane.ERROR_MESSAGE);
                    });

                    return null;
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

            String bucket = this.getCurrentBucket();

            if (bucket == null) {
                return;
            }

            // Bucket değiştiğinde Folder Edit açıksa
            // yeni bucket'ın kök konumunu göster.
            // Edit modu açık kalmaya devam eder.
            if (view.isLocationEditMode()) {
                view.getLocationField().setText("");
            }

            if (bucketSelectionListener != null) {
                bucketSelectionListener.accept(bucket);
            }

            /*
             * Bucket değiştiğinde:
             *
             * Tree root
             * +
             * File Table root
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

                validateAndLoadTreeSelection(
                        bucket,
                        prefix,
                        selectedPath);
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

        view.getLocationField().addActionListener(e -> {
            String enteredPath = view.getLocationField().getText();

            navigateToLocationPath(enteredPath);
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

        prepareForLocationChange();

        // Invalidate any root-folder load belonging to the previous repository.
        rootFolderLoadGeneration.incrementAndGet();

        operationGeneration.incrementAndGet();

        pendingBucketSelection = null;

        currentFileBucket = null;
        currentFilePrefix = null;

        contentLoader.clearCollationKeyCache();

        treeController.clearState();

        view.getFileTableModel().setFiles(
                Collections.emptyList());

        view.getBucketCombo().removeAllItems();

        // Reset Folder Edit state when the repository changes.
        view.getLocationField().setText("");

        updateBreadcrumb(S3TreeNode.ROOT_PREFIX);

        treeController.initializeRoot();

        setFileTableLoading(false);

        if (repository == null || repository.isEmpty()) {
            context.setActiveRepository(
                    RepositoryDefinition.EMPTY_REPOSITORY);

            updateActionStates();
            return;
        }

        context.setActiveRepository(repository);

        setFileTableLoading(true);

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
         * Invalidate the current listing before performing any incremental
         * File Table update. This also covers grouped operations and prevents
         * sorting/filtering from rebuilding the table from an old snapshot.
         */
        if (completedTask.isAffectsObjectList()) {

            String currentBucket = currentFileBucket;
            String currentPrefix = currentFilePrefix;

            boolean sourceAffectsCurrentListing =
                    Objects.equals(currentBucket, completedTask.getBucket());

            boolean targetAffectsCurrentListing =
                    Objects.equals(
                            currentBucket,
                            completedTask.getTargetBucket());

            if (sourceAffectsCurrentListing
                    || targetAffectsCurrentListing) {

                contentLoader.invalidate(
                        currentBucket,
                        currentPrefix);

                log.info(
                        "[SNAPSHOT INVALIDATE] type={} "
                                + "currentBucket={} currentPrefix={} "
                                + "sourceBucket={} targetBucket={} key={} targetKey={}",
                        completedTask.getType(),
                        currentBucket,
                        currentPrefix,
                        completedTask.getBucket(),
                        completedTask.getTargetBucket(),
                        completedTask.getObjectKey(),
                        completedTask.getTargetObjectKey());
            }
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
         * ---------------------------------------------------------
         * MULTI-FILE UPLOAD GROUP
         * ---------------------------------------------------------
         *
         * Folder uploads keep their existing completion flow.
         * File upload groups insert every successfully completed
         * task, then restore the complete multi-selection.
         */
        if (group.getOperation() == TransferType.UPLOAD_GROUP
                && !group.isSourceFolder()) {

            if (completedTasks.isEmpty()) {
                log.warn(
                        "[UPLOAD GROUP COMPLETED] no completed tasks group={}",
                        group.getDisplayName());
                return;
            }

            /*
             * A grouped upload must not use the old single-key
             * selection mechanism.
             */
            pendingFileTableSelectionKey = null;
            restoreFileTableFocus = false;

            List<TransferTask> uploadTasks =
                    new ArrayList<>(completedTasks);

            SwingUtilities.invokeLater(() -> {

                List<String> uploadedKeys =
                        new ArrayList<>();

                for (TransferTask task : uploadTasks) {
                    if (task == null
                            || task.getType() != TransferType.UPLOAD) {
                        continue;
                    }

                    String targetBucket =
                            task.getTargetBucket();

                    String targetKey =
                            task.getTargetObjectKey();

                    if (targetBucket == null
                            || targetKey == null
                            || targetKey.isBlank()) {
                        continue;
                    }

                    /*
                     * Only select keys belonging to the current table.
                     */
                    if (Objects.equals(currentFileBucket, targetBucket)
                            && Objects.equals(
                            currentFilePrefix,
                            getParentPrefix(targetKey))) {

                        uploadedKeys.add(targetKey);
                    }

                    /*
                     * This method inserts the row only when its
                     * target folder matches the currently open table.
                     */
                    addUploadedFileToCurrentFileTable(task);
                }

                log.info(
                        "[UPLOAD GROUP TABLE UPDATE] group={} uploadedKeys={}",
                        group.getDisplayName(),
                        uploadedKeys);

                /*
                 * Run selection after all row insertions and sorter
                 * updates have had an opportunity to complete.
                 */
                SwingUtilities.invokeLater(() -> {
                    if (!uploadedKeys.isEmpty()) {
                        restoreFileTableSelectionByKeys(
                                uploadedKeys);

                        restoreFileTableFocus = true;
                        restoreFileTableFocus();

                        log.info(
                                "[UPLOAD GROUP SELECTION RESTORE] group={} selectedKeys={}",
                                group.getDisplayName(),
                                uploadedKeys);
                    }
                });
            });

            return;
        }

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

                    if (Objects.equals(currentBucket, targetBucket)) {

                        boolean targetIsFolder = targetKey.endsWith("/");
                        String effectivePrefix =
                                currentPrefix == null ? "" : currentPrefix;

                        if (flatBrowsingEnabled) {

                            /*
                             * Flat modda, etkin klasörün altındaki bütün
                             * hedef nesneleri göreli yollarıyla ekle.
                             */
                            if (targetKey.startsWith(effectivePrefix)
                                    && targetKey.length() > effectivePrefix.length()) {

                                String relativeKey =
                                        targetKey.substring(effectivePrefix.length());

                                /*
                                 * Dosya iç içe bir klasörün altındaysa,
                                 * eksik üst klasör satırlarını da oluştur.
                                 */
                                int slashIndex = relativeKey.indexOf('/');

                                while (slashIndex >= 0) {
                                    String folderKey =
                                            effectivePrefix
                                                    + relativeKey.substring(0, slashIndex + 1);

                                    if (!folderKey.equals(targetKey)) {
                                        S3FileItem folderItem =
                                                new S3FileItem(
                                                        task.getTargetRepositoryId(),
                                                        targetBucket,
                                                        folderKey,
                                                        0,
                                                        null,
                                                        null,
                                                        true,
                                                        getFlatDisplayName(
                                                                folderKey,
                                                                effectivePrefix,
                                                                true));

                                        view.getFileTableModel().addFile(folderItem);
                                    }

                                    slashIndex =
                                            relativeKey.indexOf('/', slashIndex + 1);
                                }

                                S3FileItem item =
                                        new S3FileItem(
                                                task.getTargetRepositoryId(),
                                                targetBucket,
                                                targetKey,
                                                task.getSize(),
                                                null,
                                                null,
                                                targetIsFolder,
                                                getFlatDisplayName(
                                                        targetKey,
                                                        effectivePrefix,
                                                        targetIsFolder));

                                boolean added =
                                        view.getFileTableModel().addFile(item);

                                log.info(
                                        "[FILE TABLE GROUP INSERT] target={}/{} flat=true added={}",
                                        targetBucket,
                                        targetKey,
                                        added);
                            }

                        } else if (Objects.equals(currentPrefix, targetParentPrefix)) {

                            /*
                             * Normal modda yalnızca doğrudan alt öğeleri göster.
                             */
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
                                    view.getFileTableModel().addFile(item);

                            log.info(
                                    "[FILE TABLE GROUP INSERT] target={}/{} flat=false added={}",
                                    targetBucket,
                                    targetKey,
                                    added);

                        } else if (targetKey.startsWith(effectivePrefix)
                                && targetKey.length() > effectivePrefix.length()) {

                            /*
                             * Normal modda iç içe dosyaları ayrı ayrı gösterme.
                             * Bunun yerine ilk alt klasör satırını oluştur.
                             */
                            String relativeKey =
                                    targetKey.substring(effectivePrefix.length());

                            int slashIndex = relativeKey.indexOf('/');

                            if (slashIndex >= 0) {
                                String folderKey =
                                        effectivePrefix
                                                + relativeKey.substring(0, slashIndex + 1);

                                S3FileItem folderItem =
                                        new S3FileItem(
                                                task.getTargetRepositoryId(),
                                                targetBucket,
                                                folderKey,
                                                0,
                                                null,
                                                null,
                                                true);

                                view.getFileTableModel().addFile(folderItem);
                            }
                        }
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
             * Klasör silindiğinde, içindeki dosyaların DELETE task'ları
             * klasörün kendi satırını kaldırmak için yeterli olmayabilir.
             * Kaynak klasör satırını ayrıca kaldır.
             */
            if (group.getOperation() == TransferType.DELETE_GROUP
                    && group.isSourceFolder()) {

                String sourceBucket = group.getSourceBucket();
                String sourcePrefix = group.getSourcePrefix();

                if (sourcePrefix == null || sourcePrefix.isBlank()) {
                    sourcePrefix = event.getPrefix();
                }

                if (sourceBucket == null) {
                    sourceBucket = event.getBucket();
                }

                if (sourcePrefix != null
                        && !sourcePrefix.isBlank()
                        && Objects.equals(currentBucket, sourceBucket)) {

                    boolean folderIsVisible =
                            flatBrowsingEnabled
                                    ? currentPrefix != null
                                    && sourcePrefix.startsWith(currentPrefix)
                                    && !sourcePrefix.equals(currentPrefix)
                                    : Objects.equals(
                                    currentPrefix,
                                    getParentPrefix(sourcePrefix));

                    if (folderIsVisible) {
                        int removedCount =
                                view.getFileTableModel()
                                        .removeFilesByKeyPrefix(
                                                sourcePrefix,
                                                flatBrowsingEnabled);

                        log.info(
                                "[FILE TABLE DELETE FOLDER] prefix={} flat={} removed={}",
                                sourcePrefix,
                                flatBrowsingEnabled,
                                removedCount);
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
         * DELETE GROUP - FOLDER TREE + FILE TABLE
         * =========================================================
         */
        if (group.getOperation() == TransferType.DELETE_GROUP
                && event.isSourceRefreshRequired()) {

            String sourceBucket =
                    group.getSourceBucket();

            String sourcePrefix =
                    group.getSourcePrefix();

            if (sourcePrefix == null || sourcePrefix.isBlank()) {
                sourcePrefix = event.getPrefix();
            }

            if (sourceBucket == null) {
                sourceBucket = event.getBucket();
            }

            if (sourcePrefix != null
                    && !sourcePrefix.isBlank()
                    && Objects.equals(currentBucket, sourceBucket)) {

                /*
                 * Folder Tree'den klasörün kendisini kaldır.
                 * İçerikteki DELETE task'larının objectKey değerlerine
                 * bağımlı değiliz.
                 */
                String folderPrefix =
                        sourcePrefix.endsWith("/")
                                ? sourcePrefix
                                : sourcePrefix + "/";

                log.info(
                        "[EXPLORER GROUP DELETE TREE REFRESH] "
                                + "group={} prefix={}",
                        group.getDisplayName(),
                        folderPrefix);

                refreshScheduler.scheduleRefresh(
                        List.of(
                                new RefreshTreeNode(
                                        folderPrefix,
                                        RefreshTreeOperation.DELETE)));
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
             * Kaynak klasörün kendisini group üzerinden bul.
             * Tek tek task objectKey değerlerine güvenme;
             * görevler klasörün içindeki dosyaları temsil edebilir.
             */
            if (group.getOperation() == TransferType.MOVE_GROUP) {

                String sourceBucket = group.getSourceBucket();
                String sourcePrefix = group.getSourcePrefix();

                if (sourceBucket == null) {
                    sourceBucket = event.getBucket();
                }

                if (sourcePrefix == null || sourcePrefix.isBlank()) {
                    sourcePrefix = event.getPrefix();
                }

                if (sourcePrefix != null
                        && !sourcePrefix.isBlank()
                        && Objects.equals(currentBucket, sourceBucket)) {

                    String folderPrefix = sourcePrefix.endsWith("/")
                            ? sourcePrefix
                            : sourcePrefix + "/";

                    List<RefreshTreeNode> sourceTreeRefreshes =
                            List.of(
                                    new RefreshTreeNode(
                                            folderPrefix,
                                            RefreshTreeOperation.DELETE));

                    log.info(
                            "[EXPLORER SOURCE FOLDER TREE REFRESH] "
                                    + "group={} operation={} sourceBucket={} "
                                    + "sourcePrefix={} prefixes={}",
                            group.getDisplayName(),
                            group.getOperation(),
                            sourceBucket,
                            folderPrefix,
                            sourceTreeRefreshes);

                    refreshScheduler.scheduleRefresh(sourceTreeRefreshes);
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
        refreshGroupTargetViews(
                group,
                completedTasks,
                currentBucket,
                currentPrefix);

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
        updateNavigationButtonStates();
    }

    private void addBreadcrumbButton(String text, String prefix) {
        JButton button;
        if (!S3TreeNode.ROOT_PREFIX.equals(prefix)) {
            button = new JButton(text + " /");
        }
        else {
            button = new JButton("", IconProvider.ICON_SYSTEM_CLOSED_FOLDER);
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

    private NavigationEntry getCurrentNavigationEntry() {
        RepositoryDefinition repository = getCurrentRepository();
        String bucket = getCurrentBucket();
        String prefix = getCurrentPrefix();

        if (repository == null
                || repository.isEmpty()
                || bucket == null) {
            return null;
        }

        return new NavigationEntry(
                repository.getId(),
                bucket,
                prefix == null ? S3TreeNode.ROOT_PREFIX : prefix);
    }

    private void recordNavigation() {
        if (navigationPlayback) {
            return;
        }

        NavigationEntry current = getCurrentNavigationEntry();

        if (current == null) {
            return;
        }

        if (navigationHistoryIndex >= 0
                && navigationHistoryIndex < navigationHistory.size()
                && navigationHistory.get(navigationHistoryIndex).equals(current)) {
            updateNavigationButtonStates();
            return;
        }

        // Geri gittikten sonra yeni bir yere gidilirse ileri geçmişi sil.
        while (navigationHistory.size() > navigationHistoryIndex + 1) {
            navigationHistory.remove(navigationHistory.size() - 1);
        }

        navigationHistory.add(current);
        navigationHistoryIndex = navigationHistory.size() - 1;

        updateNavigationButtonStates();
    }

    private void updateNavigationButtonStates() {
        if (view == null) {
            return;
        }

        boolean canGoBack = navigationHistoryIndex > 0;

        boolean canGoForward =
                navigationHistoryIndex >= 0
                        && navigationHistoryIndex
                        < navigationHistory.size() - 1;

        view.setNavigationButtonStates(
                canGoBack,
                canGoForward);

        backAction.setEnabled(canGoBack);
        forwardAction.setEnabled(canGoForward);

        clearHistoryAction.setEnabled(
                !navigationHistory.isEmpty()
                        && pendingNavigationTarget == null);
    }

    private void goBack() {
        if (navigationHistoryIndex <= 0) {
            return;
        }

        navigateHistoryTo(navigationHistoryIndex - 1);
    }

    private void goForward() {
        if (navigationHistoryIndex < 0
                || navigationHistoryIndex >= navigationHistory.size() - 1) {
            return;
        }

        navigateHistoryTo(navigationHistoryIndex + 1);
    }


    private void navigateHistoryTo(int targetIndex) {
        if (targetIndex < 0
                || targetIndex >= navigationHistory.size()) {
            return;
        }

        NavigationEntry target =
                navigationHistory.get(targetIndex);

        NavigationEntry current =
                getCurrentNavigationEntry();


        if (target.equals(current)) {

            boolean currentIndexMatches =
                    navigationHistoryIndex >= 0
                            && navigationHistoryIndex
                            < navigationHistory.size()
                            && navigationHistory.get(
                                    navigationHistoryIndex)
                            .equals(current);

            if (!currentIndexMatches) {
                int actualIndex =
                        navigationHistory.lastIndexOf(current);

                if (actualIndex >= 0) {
                    navigationHistoryIndex = actualIndex;
                }
            }

            updateNavigationButtonStates();
            return;
        }

        pendingNavigationPreviousIndex = navigationHistoryIndex;
        navigationHistoryIndex = targetIndex;
        pendingNavigationTarget = target;
        navigationPlayback = true;

        RepositoryDefinition currentRepository =
                getCurrentRepository();

        if (currentRepository == null
                || currentRepository.isEmpty()
                || !Objects.equals(
                currentRepository.getId(),
                target.repositoryId())) {

            RepositoryDefinition targetRepository =
                    repositoryManager.findById(
                            target.repositoryId());

            if (targetRepository == null
                    || targetRepository.isEmpty()) {

                cancelPendingNavigation(
                        "The repository in navigation history no longer exists: "
                                + target.repositoryId());
                return;
            }

            boolean foundInCombo = false;

            for (int i = 0;
                 i < view.getRepositoryCombo().getItemCount();
                 i++) {

                RepositoryDefinition item =
                        view.getRepositoryCombo().getItemAt(i);

                if (item != null
                        && Objects.equals(
                        item.getId(),
                        target.repositoryId())) {

                    view.getRepositoryCombo()
                            .setSelectedItem(item);

                    foundInCombo = true;
                    break;
                }
            }

            if (!foundInCombo) {
                cancelPendingNavigation(
                        "The repository in navigation history could not be found: "
                                + target.repositoryId());
                return;
            }

            /*
             * Repository seçimi bucket yükleme akışını başlatır.
             */
            setSelectedRepository(targetRepository);
            return;
        }

        if (!Objects.equals(
                getCurrentBucket(),
                target.bucket())) {

            selectBucketForPendingNavigation(target);
            return;
        }

        restorePendingNavigationPrefix(target);
    }


    private void removeInvalidPendingNavigation(
            NavigationEntry target) {

        if (target != null) {
            removeHistoryEntriesForPrefix(
                    target.repositoryId(),
                    target.bucket(),
                    target.prefix());
        }

        pendingNavigationTarget = null;
        pendingNavigationPreviousIndex = -1;
        navigationPlayback = false;

        updateNavigationButtonStates();
    }

    private void removeHistoryEntriesForPrefix(
            String repositoryId,
            String bucket,
            String deletedPrefix) {

        if (repositoryId == null
                || bucket == null
                || deletedPrefix == null
                || deletedPrefix.isBlank()
                || Objects.equals(
                deletedPrefix,
                S3TreeNode.ROOT_PREFIX)) {
            return;
        }

        String normalizedPrefix =
                deletedPrefix.endsWith("/")
                        ? deletedPrefix
                        : deletedPrefix + "/";

        NavigationEntry currentEntry =
                getCurrentNavigationEntry();

        int oldIndex = navigationHistoryIndex;
        int removedAtOrBeforeIndex = 0;

        for (int i = navigationHistory.size() - 1;
             i >= 0;
             i--) {

            NavigationEntry entry =
                    navigationHistory.get(i);

            boolean sameRepositoryAndBucket =
                    Objects.equals(
                            entry.repositoryId(),
                            repositoryId)
                            && Objects.equals(
                            entry.bucket(),
                            bucket);

            boolean belongsToDeletedSubtree =
                    Objects.equals(
                            entry.prefix(),
                            normalizedPrefix)
                            || (entry.prefix() != null
                            && entry.prefix().startsWith(
                            normalizedPrefix));

            if (sameRepositoryAndBucket
                    && belongsToDeletedSubtree) {

                if (i <= oldIndex) {
                    removedAtOrBeforeIndex++;
                }

                navigationHistory.remove(i);
            }
        }

        if (navigationHistory.isEmpty()) {
            navigationHistoryIndex = -1;
        } else if (currentEntry != null
                && navigationHistory.contains(currentEntry)) {

            // Gerçekte görüntülenen konumu geçmişte koru.
            navigationHistoryIndex =
                    navigationHistory.lastIndexOf(currentEntry);

        } else {
            navigationHistoryIndex = Math.max(
                    0,
                    Math.min(
                            oldIndex - removedAtOrBeforeIndex,
                            navigationHistory.size() - 1));
        }

        log.info(
                "[HISTORY CLEANUP] repository={} bucket={} "
                        + "deletedPrefix={} historySize={} index={}",
                repositoryId,
                bucket,
                normalizedPrefix,
                navigationHistory.size(),
                navigationHistoryIndex);

        updateNavigationButtonStates();
    }

    private void cancelPendingNavigation(String message) {
        NavigationEntry invalidTarget =
                pendingNavigationTarget;

        if (invalidTarget != null) {
            removeInvalidPendingNavigation(invalidTarget);
        } else {
            pendingNavigationPreviousIndex = -1;
            navigationPlayback = false;
            updateNavigationButtonStates();
        }

        JOptionPane.showMessageDialog(
                this,
                message,
                "Navigation History",
                JOptionPane.ERROR_MESSAGE);
    }



    private void restorePendingNavigationPrefix(
            NavigationEntry target) {

        if (target == null) {
            return;
        }

        if (Objects.equals(
                target.prefix(),
                S3TreeNode.ROOT_PREFIX)) {

            pendingNavigationTarget = null;
            pendingNavigationPreviousIndex = -1;
            navigationPlayback = false;

            treeController.selectPrefix(
                    S3TreeNode.ROOT_PREFIX);

            updateNavigationButtonStates();
            return;
        }

        /*
         * Önce S3 üzerinde klasörün gerçekten var olduğunu doğrula.
         * Bu kontrol, ağaç önbelleğindeki eski kayıtlara güvenmez.
         */
        CompletableFuture.supplyAsync(
                () -> getService().folderExists(
                        target.bucket(),
                        target.prefix()),
                explorerPool
        ).whenComplete((exists, exception) ->
                SwingUtilities.invokeLater(() -> {

                    /*
                     * Kontrol sürerken başka bir navigasyon
                     * başlatılmışsa eski sonucu uygulama.
                     */
                    if (!Objects.equals(
                            pendingNavigationTarget,
                            target)) {
                        return;
                    }

                    /*
                     * S3 bağlantı/yetki hatasını klasör silinmiş
                     * gibi değerlendirme. Geçmiş kaydı korunur.
                     */
                    if (exception != null) {
                        navigationHistoryIndex =
                                pendingNavigationPreviousIndex;

                        pendingNavigationTarget = null;
                        pendingNavigationPreviousIndex = -1;
                        navigationPlayback = false;

                        updateNavigationButtonStates();

                        log.error(
                                "[HISTORY VALIDATION] failed bucket={} prefix={}",
                                target.bucket(),
                                target.prefix(),
                                exception);

                        JOptionPane.showMessageDialog(
                                this,
                                "The folder could not be verified because "
                                        + "an S3 operation failed.\n"
                                        + "Please check the connection and permissions.",
                                "Navigation History",
                                JOptionPane.ERROR_MESSAGE);

                        return;
                    }

                    /*
                     * Klasör başka bir uygulama tarafından silinmiş.
                     */

                    if (!Boolean.TRUE.equals(exists)) {
                        recoverMissingHistoryTarget(target);
                        return;
                    }

                    /*
                     * S3 üzerinde mevcut. Şimdi ağaçta seç.
                     */
                    treeController.selectPrefix(
                            target.prefix(),
                            success -> {

                                if (!Objects.equals(
                                        pendingNavigationTarget,
                                        target)) {
                                    return;
                                }

                                if (!success) {
                                    treeController.removePrefix(
                                            target.prefix());

                                    cancelPendingNavigation(
                                            "The folder could not be selected: "
                                                    + target.prefix());
                                    return;
                                }

                                SwingUtilities.invokeLater(() -> {

                                    if (Objects.equals(
                                            pendingNavigationTarget,
                                            target)
                                            && Objects.equals(
                                            getCurrentPrefix(),
                                            target.prefix())
                                            && Objects.equals(
                                            getCurrentBucket(),
                                            target.bucket())) {

                                        pendingNavigationTarget = null;
                                        pendingNavigationPreviousIndex = -1;
                                        navigationPlayback = false;
                                    }

                                    updateNavigationButtonStates();
                                });
                            });
                }));
    }

    private void selectBucketForPendingNavigation(
            NavigationEntry target) {

        if (target == null) {
            return;
        }

        JComboBox<String> combo = view.getBucketCombo();

        for (int i = 0; i < combo.getItemCount(); i++) {
            if (Objects.equals(combo.getItemAt(i), target.bucket())) {
                combo.setSelectedItem(target.bucket());
                return;
            }
        }

        cancelPendingNavigation(
                "Previous bucket does not exists anymore: " + target.bucket());
    }

    private void changeFolderEditStatus() {
        boolean editMode = !view.isLocationEditMode();

        if (editMode) {
            NavigationEntry current = getCurrentNavigationEntry();

            String prefix = current == null || current.prefix() == null
                    ? ""
                    : current.prefix();

            view.getLocationField().setText(prefix);
        }

        view.setLocationEditMode(editMode);

        if (!editMode) {
            updateBreadcrumb(getCurrentPrefix());
        }

        // Kullanıcının tercihini kalıcı ayarlara bildir.
        if (folderEditModeSelectionListener != null) {
            folderEditModeSelectionListener.accept(editMode);
        }
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
                        S3Util.getOperationGroupName(items),
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
                        S3Util.getOperationGroupName(items),
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
                        "Repository Manager",
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
        reloadCurrentFileTable(false);
    }

    private void reloadCurrentFileTable(
            boolean filterRefresh) {

        String bucket = currentFileBucket;
        String prefix = currentFilePrefix;

        if (bucket == null) {
            return;
        }

        if (prefix == null) {
            prefix = S3TreeNode.ROOT_PREFIX;
        }

        log.debug(
                "[FILE TABLE RELOAD] bucket={} prefix={} limit={} filterRefresh={}",
                bucket,
                prefix,
                getSelectedFileTableRowLimit(),
                filterRefresh);

        loadFiles(
                bucket,
                prefix,
                false,
                filterRefresh);
    }

    private FileTableFilter getCurrentFileTableFilter() {
        String text = view.getFilterText();
        String selectedType = view.getItemTypeFilter();

        ItemTypeFilter itemType = switch (
                selectedType == null ? "All" : selectedType) {
            case "Folders Only" -> ItemTypeFilter.FOLDERS_ONLY;
            case "Files Only" -> ItemTypeFilter.FILES_ONLY;
            default -> ItemTypeFilter.ALL;
        };

        return new FileTableFilter(text, itemType);
    }

    private void reloadCurrentFolder() {

        String bucket = currentFileBucket != null
                ? currentFileBucket
                : getCurrentBucket();

        String prefix = getCurrentPrefix();

        RepositoryDefinition repository = getCurrentRepository();

        if (bucket == null || bucket.isBlank()
                || repository == null
                || repository.isEmpty()
                || prefix == null) {
            return;
        }

        String repositoryId = repository.getId();
        S3ExplorerService service = getService();

        CompletableFuture.supplyAsync(
                () -> prefix.isBlank()
                        || Boolean.TRUE.equals(
                        service.folderExists(bucket, prefix)),
                explorerPool
        ).whenComplete((exists, exception) ->
                SwingUtilities.invokeLater(() -> {

                    // Kontrol sürerken konum değiştiyse eski sonucu uygulama.
                    RepositoryDefinition active = getCurrentRepository();

                    if (active == null
                            || !Objects.equals(active.getId(), repositoryId)
                            || !Objects.equals(getCurrentBucket(), bucket)
                            || !Objects.equals(getCurrentPrefix(), prefix)) {
                        return;
                    }

                    if (exception != null) {
                        log.error(
                                "[RELOAD FOLDER] validation failed bucket={} prefix={}",
                                bucket, prefix, exception);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver.getUserMessage(exception),
                                "Folder Reload Failed",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    if (!Boolean.TRUE.equals(exists)) {
                        recoverMissingReloadFolder(
                                service,
                                repositoryId,
                                bucket,
                                prefix);
                        return;
                    }

                    contentLoader.invalidate(bucket, prefix);

                    treeController.refreshNode(
                            new RefreshTreeNode(
                                    prefix,
                                    RefreshTreeOperation.REFRESH));

                    reloadCurrentFileTable();
                }));
    }

    private List<S3FileItem> createFileTableRows(
            String repositoryId,
            String bucket,
            String prefix,
            LimitedFolderContent content,
            boolean flatBrowsing) {

        List<S3FileItem> rows = new ArrayList<>();

        // Parent folder: root'ta gösterilmez.
        if (prefix != null && !prefix.isEmpty()) {
            rows.add(new S3FileItem(
                    repositoryId,
                    bucket,
                    prefix + S3FileItem.PARENT_FOLDER_NAME,
                    0,
                    null,
                    null,
                    true));
        }

        // Tüm klasörler.
        for (String folder : content.folders()) {
            rows.add(new S3FileItem(
                    repositoryId,
                    bucket,
                    folder,
                    0,
                    null,
                    null,
                    true,
                    flatBrowsing
                            ? getFlatDisplayName(folder, prefix, true)
                            : null));
        }

        // Bounded collection'da bulunan dosyalar.
        for (var object : content.files()) {
            if (object.key().endsWith("/")) {
                continue;
            }

            rows.add(new S3FileItem(
                    repositoryId,
                    bucket,
                    object.key(),
                    object.size(),
                    object.lastModified(),
                    object.storageClass() == null
                            ? null
                            : object.storageClass().toString(),
                    false,
                    flatBrowsing
                            ? getFlatDisplayName(
                            object.key(), prefix, false)
                            : null));
        }

        return rows;
    }



    private void applyPreparedFileTableRows(
            List<S3FileItem> rows,
            LimitedFolderContent content,
            String bucket,
            String prefix) {

        currentFileTableLimitReached = content.fileLimitReached();

        // Update the model first.
        // FileTableRowSorter.allRowsChanged() will consume pending sort
        // keys and apply them to the new rows, avoiding a sort of stale rows.
        view.getFileTableModel().setFiles(rows);

        view.getFileTable().getTableHeader().repaint();

        updateFileFolderInfo(content);

        log.debug(
                "[FILE TABLE APPLY] bucket={} prefix={} folders={} files={} scannedFiles={} limitReached={}",
                bucket,
                prefix,
                content.folderCount(),
                content.fileCount(),
                content.totalMatchingFileCount(),
                content.fileLimitReached());
    }

    private void updateFileFolderInfo(
            LimitedFolderContent content) {

        long folderCount = content.folderCount();
        long fileCount = content.fileCount();

        String folderText = folderCount > 0
                ? formatCount(folderCount, "folder")
                : "";

        String fileText = "";

        if (fileCount > 0 || content.fileLimitReached()) {
            fileText = formatCount(fileCount, "file");

            if (content.fileLimitReached()) {
                fileText += " / "
                        + formatCount(
                        content.totalMatchingFileCount(),
                        "file");
            }
        }

        String infoText;

        if (folderText.isEmpty()) {
            infoText = fileText;
        } else if (fileText.isEmpty()) {
            infoText = folderText;
        } else {
            infoText = folderText + " and " + fileText;
        }

        view.getFileFolderInfo().setText(infoText);
    }

    private void updateSelectedFileInfo() {

        JTable table = view.getFileTable();
        FileTableModel model = view.getFileTableModel();

        int folderCount = 0;
        int fileCount = 0;
        long totalSize = 0;

        for (int viewRow : table.getSelectedRows()) {

            int modelRow =
                    table.convertRowIndexToModel(viewRow);

            S3FileItem item = model.getItem(modelRow);

            if (item == null || item.isParentFolder()) {
                continue;
            }

            if (item.isFolder()) {
                folderCount++;
            } else {
                fileCount++;
                totalSize += item.getSize();
            }
        }

        JLabel selectionInfo = view.getFileSelectionInfo();

        if (folderCount == 0 && fileCount == 0) {
            selectionInfo.setText("");
            selectionInfo.setVisible(false);
            view.setFileSelectionSeparatorVisible(false);
            return;
        }

        String folderText = folderCount > 0
                ? formatCount(folderCount, "folder")
                : "";

        String fileText = fileCount > 0
                ? formatCount(fileCount, "file")
                : "";

        String selectedText;

        if (folderText.isEmpty()) {
            selectedText = fileText;
        } else if (fileText.isEmpty()) {
            selectedText = folderText;
        } else {
            selectedText = folderText + " and " + fileText;
        }

        if (fileCount > 0) {
            selectedText += " ("
                    + SizeFormatter.format(totalSize)
                    + ")";
        }

        selectionInfo.setText(selectedText + " selected");
        selectionInfo.setVisible(true);
        view.setFileSelectionSeparatorVisible(true);
    }

    private void updateFileDiscoveryProgress(
            long generation,
            String bucket,
            String prefix,
            long fileCount,
            long folderCount) {

        SwingUtilities.invokeLater(() -> {

            if (generation !=
                    fileDiscoveryGeneration.get()) {

                return;
            }

            view.getFileFolderInfo().setText(
                    "Preparing file table... "
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

        return createOperationDialog(
                title,
                new Dimension(450, 150));
    }

    private OperationDialog createOperationDialog(
            String title,
            Dimension minimumSize) {

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
                        14,
                        18,
                        14,
                        18));

        JLabel message =
                new JLabel("Preparing file table...");

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

        dialog.setMinimumSize(minimumSize);

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

                case BUCKET:

                    if (bucketDialog == null) {
                        bucketDialog =
                                createOperationDialog(
                                        "Bucket Loading");
                    }

                    operationDialog = bucketDialog;
                    break;

                case FILE_TABLE:

                    if (fileTableDialog == null) {
                        fileTableDialog =
                                createOperationDialog(
                                        "File Table");
                    }

                    operationDialog = fileTableDialog;
                    break;

                case FILTER_REFRESH:

                    if (filterRefreshDialog == null) {
                        filterRefreshDialog =
                                createOperationDialog(
                                        "Updating File Table",
                                        new Dimension(320, 95));
                    }

                    operationDialog = filterRefreshDialog;
                    break;

                default:
                    return;
            }

            operationDialog.message.setText(message);
            operationDialog.dialog.pack();

            if (operationDialog.showTimer != null) {
                operationDialog.showTimer.stop();
            }

            if (!visibleOperationDialogs.contains(operationDialog)) {
                visibleOperationDialogs.add(operationDialog);
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

            OperationDialog operationDialog;

            switch (type) {

                case BUCKET:
                    operationDialog = bucketDialog;
                    break;

                case FILE_TABLE:
                    operationDialog = fileTableDialog;
                    break;

                case FILTER_REFRESH:
                    operationDialog = filterRefreshDialog;
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

                visibleOperationDialogs.remove(operationDialog);
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
                        SwingUtilities.getWindowAncestor(this),
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

    private void showProperties() {
        JTable table = view.getFileTable();

        List<S3FileItem> selectedItems =
                getSelectedFileItems();

        if (selectedItems.isEmpty()
                || selectedItems.size() != table.getSelectedRowCount()) {
            return;
        }

        Window owner =
                S3Util.getMainFrameAncestor(this);

        if (selectedItems.size() == 1) {
            S3FileItem item = selectedItems.get(0);

            PropertiesDialog dialog =
                    new PropertiesDialog(owner, item);

            if (item.isFolder()) {
                explorerPool.submit(() -> {
                    try {
                        /*
                         * Check whether an object exists at the exact
                         * folder key. A trailing slash is required.
                         */
                        String folderKey =
                                item.getKey().endsWith("/")
                                        ? item.getKey()
                                        : item.getKey() + "/";

                        var placeholder =
                                getService().getObject(
                                        item.getBucket(),
                                        folderKey);

                        SwingUtilities.invokeLater(() ->
                                dialog.updateFolderType(
                                        placeholder != null,
                                        placeholder == null
                                                ? null
                                                : placeholder.lastModified()));

                        FolderProperties properties =
                                getService().calculateFolderProperties(
                                        item.getBucket(),
                                        folderKey,
                                        progress ->
                                                SwingUtilities.invokeLater(() ->
                                                        dialog.updateFolderProgress(
                                                                progress)));

                        SwingUtilities.invokeLater(() -> {
                            dialog.updateFolderProperties(properties);
                            dialog.setCalculationCompleted();
                        });

                    } catch (Exception ex) {
                        log.error(
                                "[PROPERTIES] folder calculation failed "
                                        + "bucket={} prefix={}",
                                item.getBucket(),
                                item.getKey(),
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
            return;
        }

        PropertiesDialog dialog =
                new PropertiesDialog(owner, selectedItems);

        List<S3FileItem> selectedFiles =
                selectedItems.stream()
                        .filter(S3FileItem::isFile)
                        .toList();

        List<S3FileItem> selectedFolders =
                selectedItems.stream()
                        .filter(S3FileItem::isFolder)
                        .sorted(Comparator.comparingInt(
                                item -> item.getKey().length()))
                        .toList();

        // Avoid calculating a selected folder again when an ancestor
        // folder is already included in the selection.
        List<S3FileItem> foldersToCalculate =
                new ArrayList<>();

        for (S3FileItem folder : selectedFolders) {
            boolean coveredBySelectedFolder =
                    foldersToCalculate.stream()
                            .anyMatch(parent ->
                                    folder.getBucket().equals(parent.getBucket())
                                            && folder.getKey().startsWith(
                                            parent.getKey()));

            if (!coveredBySelectedFolder) {
                foldersToCalculate.add(folder);
            }
        }

        long selectedFileCount = selectedFiles.size();

        long selectedFileSize =
                selectedFiles.stream()
                        .mapToLong(S3FileItem::getSize)
                        .sum();

        explorerPool.submit(() -> {
            long completedFolderCount = 0;
            long completedFileCount = 0;
            long completedSize = 0;

            try {
                for (S3FileItem folder : foldersToCalculate) {
                    final long previousFolderCount =
                            completedFolderCount;

                    final long previousFileCount =
                            completedFileCount;

                    final long previousSize =
                            completedSize;

                    FolderProperties properties =
                            getService().calculateFolderProperties(
                                    folder.getBucket(),
                                    folder.getKey(),
                                    progress -> {
                                        FolderProperties aggregate =
                                                new FolderProperties(
                                                        previousFolderCount
                                                                + progress.folderCount(),
                                                        selectedFileCount
                                                                + previousFileCount
                                                                + progress.fileCount(),
                                                        selectedFileSize
                                                                + previousSize
                                                                + progress.totalSize());

                                        SwingUtilities.invokeLater(() ->
                                                dialog.updateFolderProgress(
                                                        aggregate));
                                    });

                    completedFolderCount +=
                            properties.folderCount();

                    completedFileCount +=
                            properties.fileCount();

                    completedSize +=
                            properties.totalSize();
                }

                FolderProperties result =
                        new FolderProperties(
                                completedFolderCount,
                                selectedFileCount + completedFileCount,
                                selectedFileSize + completedSize);

                SwingUtilities.invokeLater(() -> {
                    dialog.updateFolderProperties(result);
                    dialog.setCalculationCompleted();
                });

            } catch (Exception ex) {
                log.error(
                        "[PROPERTIES] multi-selection calculation failed",
                        ex);

                SwingUtilities.invokeLater(() ->
                        dialog.setStatus(
                                "Calculation failed: "
                                        + S3ErrorResolver
                                        .getDetailedMessage(ex)));
            }
        });

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

    private void refreshGroupTargetViews(
            TransferGroup group,
            List<TransferTask> completedTasks,
            String currentBucket,
            String currentPrefix) {

        if (group == null
                || completedTasks == null
                || completedTasks.isEmpty()) {
            return;
        }

        TransferType operation = group.getOperation();

        boolean isGroupCopyOrMove =
                operation == TransferType.COPY_GROUP
                        || operation == TransferType.MOVE_GROUP;

        boolean isUploadGroup =
                operation == TransferType.UPLOAD_GROUP;

        boolean isTargetOperation =
                operation == TransferType.COPY
                        || operation == TransferType.MOVE
                        || isGroupCopyOrMove
                        || isUploadGroup;

        if (!isTargetOperation) {
            return;
        }

        String targetBucket = group.getTargetBucket();
        String targetPrefix = group.getTargetPrefix();

        if (targetBucket == null
                || !Objects.equals(currentBucket, targetBucket)) {
            return;
        }

        Set<RefreshTreeNode> targetTreeRefreshes =
                new LinkedHashSet<>();

        if (isGroupCopyOrMove) {

            for (TransferTask task : completedTasks) {

                if (task == null
                        || !Objects.equals(
                        targetBucket,
                        task.getTargetBucket())) {
                    continue;
                }

                Set<RefreshTreeNode> affectedPrefixes =
                        task.getAffectedPrefixes();

                if (affectedPrefixes != null) {

                    for (RefreshTreeNode refreshNode : affectedPrefixes) {

                        if (refreshNode == null
                                || refreshNode.operation()
                                != RefreshTreeOperation.ADD) {
                            continue;
                        }

                        addFolderTreeRefresh(
                                targetTreeRefreshes,
                                refreshNode.prefix());
                    }
                }

                String targetObjectKey =
                        task.getTargetObjectKey();

                if (targetObjectKey != null
                        && targetObjectKey.endsWith("/")) {

                    addFolderTreeRefresh(
                            targetTreeRefreshes,
                            targetObjectKey);
                }
            }

        } else if (targetPrefix != null) {

            if (isUploadGroup) {

                /*
                 * Folder upload:
                 *
                 * targetPrefix = SIL71/SIL1/
                 *
                 * Eklenecek düğüm yüklenen klasörün kendisidir.
                 * Üst dizini (SIL71/) tekrar ADD etmemeliyiz.
                 */
                addFolderTreeRefresh(
                        targetTreeRefreshes,
                        targetPrefix);

            } else {

                /*
                 * Tekli COPY/MOVE işlemlerindeki mevcut davranış.
                 */
                targetTreeRefreshes.add(
                        new RefreshTreeNode(
                                targetPrefix,
                                RefreshTreeOperation.ADD));
            }
        }

        if (!targetTreeRefreshes.isEmpty()) {

            log.info(
                    "[EXPLORER TARGET TREE REFRESH] "
                            + "group={} operation={} prefixes={}",
                    group.getDisplayName(),
                    operation,
                    targetTreeRefreshes);

            refreshScheduler.scheduleRefresh(
                    new ArrayList<>(targetTreeRefreshes));
        }

         /*
         * UPLOAD_GROUP:
         * Yüklenen klasörün üst dizini açıksa File Table'ı yenile.
         */
        if (isUploadGroup
                && targetPrefix != null
                && Objects.equals(
                currentPrefix,
                getParentPrefix(targetPrefix))) {

            log.info(
                    "[EXPLORER UPLOAD TARGET TABLE REFRESH] "
                            + "group={} bucket={} prefix={} targetPrefix={}",
                    group.getDisplayName(),
                    currentBucket,
                    currentPrefix,
                    targetPrefix);

            refreshScheduler.scheduleCurrentTableRefresh();
        }
    }

    private void addFolderTreeRefresh(
            Set<RefreshTreeNode> refreshes,
            String prefix) {

        if (refreshes == null
                || prefix == null
                || prefix.isBlank()) {
            return;
        }

        String normalizedPrefix = prefix.endsWith("/")
                ? prefix
                : prefix + "/";

        refreshes.add(
                new RefreshTreeNode(
                        normalizedPrefix,
                        RefreshTreeOperation.ADD));
    }



    private void navigateToLocationPath(String enteredPath) {
        String bucket = getCurrentBucket();
        RepositoryDefinition repository = getCurrentRepository();

        if (bucket == null || repository == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select a repository and a bucket first.",
                    "Location",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String normalized = enteredPath == null
                ? ""
                : enteredPath.trim().replace('\\', '/');

        // Remove leading and trailing separators.
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (!normalized.isEmpty() && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Validate each path segment.
        if (!normalized.isEmpty()) {
            String[] segments = normalized.split("/", -1);

            for (String segment : segments) {
                if (segment.isBlank()
                        || ".".equals(segment)
                        || "..".equals(segment)) {

                    JOptionPane.showMessageDialog(
                            this,
                            "Invalid folder path.",
                            "Location",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            normalized += "/";
        }

        final String targetPrefix = normalized;
        final String targetBucket = bucket;
        final String targetRepositoryId = repository.getId();

        // The bucket root does not require an S3 folder existence check.
        if (targetPrefix.isEmpty()) {
            if (!Objects.equals(targetPrefix, getCurrentPrefix())) {
                recordNavigation();
                treeController.selectPrefix(targetPrefix);
            } else {
                updateBreadcrumb(targetPrefix);
            }

            // Keep location edit mode enabled.
            view.getLocationField().setText("");
            return;
        }

        view.getLocationField().setEnabled(false);

        explorerPool.submit(() -> {
            boolean folderExists = false;
            Exception validationFailure = null;

            try {
                S3ExplorerService service =
                        new S3ExplorerService(clientManager.getClient(repository));

                folderExists = service.folderExists(
                        targetBucket,
                        targetPrefix);

            } catch (Exception ex) {
                validationFailure = ex;
            }

            final boolean exists = folderExists;
            final Exception failure = validationFailure;

            SwingUtilities.invokeLater(() -> {
                view.getLocationField().setEnabled(true);

                // Ignore the result if the active repository or bucket changed.
                RepositoryDefinition activeRepository = getCurrentRepository();

                if (activeRepository == null
                        || !Objects.equals(
                        targetRepositoryId,
                        activeRepository.getId())
                        || !Objects.equals(
                        targetBucket,
                        getCurrentBucket())) {
                    return;
                }

                if (failure != null) {
                    JOptionPane.showMessageDialog(
                            this,
                            S3ErrorResolver.getUserMessage(failure),
                            "Folder Validation Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (!exists) {
                    JOptionPane.showMessageDialog(
                            this,
                            "The specified folder does not exist:\n"
                                    + targetPrefix,
                            "Folder Not Found",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Update the field to the normalized path.
                view.getLocationField().setText(targetPrefix);

                if (!Objects.equals(targetPrefix, getCurrentPrefix())) {
                    recordNavigation();
                    treeController.selectPrefix(targetPrefix);
                } else {
                    updateBreadcrumb(targetPrefix);
                }

                // Do not exit edit mode here.
                // The user can return to breadcrumbs by clicking Folder Edit.
            });
        });
    }

    public void setFolderEditModeSelectionListener(
            Consumer<Boolean> listener) {
        this.folderEditModeSelectionListener = listener;
    }

    public void setFolderEditModePreference(boolean enabled) {
        String prefix = getCurrentPrefix();

        view.getLocationField().setText(
                prefix == null ? "" : prefix);

        view.setLocationEditMode(enabled);

        if (!enabled) {
            updateBreadcrumb(prefix);
        }
    }


    private void validateAndLoadTreeSelection(
            String bucket,
            String prefix,
            TreePath selectedPath) {

        /*
         * Root her zaman geçerlidir.
         */
        if (prefix == null
                || prefix.isBlank()
                || Objects.equals(
                prefix,
                S3TreeNode.ROOT_PREFIX)) {

            completeTreeSelection(bucket, prefix);
            return;
        }

        RepositoryDefinition repository =
                getCurrentRepository();

        if (repository == null || repository.isEmpty()) {
            return;
        }

        String repositoryId = repository.getId();

        CompletableFuture.supplyAsync(
                () -> new S3ExplorerService(
                        clientManager.getClient(repository))
                        .folderExists(bucket, prefix),
                explorerPool
        ).whenComplete((exists, exception) ->
                SwingUtilities.invokeLater(() -> {

                    RepositoryDefinition activeRepository =
                            getCurrentRepository();

                    /*
                     * Kontrol sürerken repository, bucket veya
                     * ağaç seçimi değiştiyse eski sonucu kullanma.
                     */
                    if (activeRepository == null
                            || !Objects.equals(
                            repositoryId,
                            activeRepository.getId())
                            || !Objects.equals(
                            bucket,
                            getCurrentBucket())) {
                        return;
                    }

                    TreePath actualPath =
                            view.getFolderTree().getSelectionPath();

                    if (actualPath == null
                            || !actualPath.equals(selectedPath)) {
                        return;
                    }

                    /*
                     * Bağlantı veya yetki hatası, silinmiş klasör
                     * olarak değerlendirilmemeli.
                     */
                    if (exception != null) {
                        log.error(
                                "[TREE SELECTION VALIDATION] failed "
                                        + "bucket={} prefix={}",
                                bucket,
                                prefix,
                                exception);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver.getUserMessage(exception),
                                "Folder Validation Failed",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }


                    if (!Boolean.TRUE.equals(exists)) {

                        log.info(
                                "[STALE TREE NODE] removing bucket={} prefix={}",
                                bucket,
                                prefix);

                        treeController.removePrefix(prefix);
                        removeHistoryEntriesForPrefix(
                                repositoryId,
                                bucket,
                                prefix);

                        // Silinen klasör, mevcut File Table'da listeleniyorsa
                        // tabloyu önbellekten değil S3'ten yeniden yükle.
                        String tableBucket = currentFileBucket;
                        String tablePrefix = currentFilePrefix;

                        boolean deletedFolderIsInCurrentTable =
                                Objects.equals(tableBucket, bucket)
                                        && Objects.equals(
                                        tablePrefix,
                                        getParentPrefix(prefix));

                        if (deletedFolderIsInCurrentTable) {
                            contentLoader.invalidate(
                                    tableBucket,
                                    tablePrefix);

                            reloadCurrentFileTable();
                        }

                        String fallbackPrefix =
                                Objects.equals(currentFileBucket, bucket)
                                        ? currentFilePrefix
                                        : null;

                        if (fallbackPrefix == null
                                || Objects.equals(fallbackPrefix, prefix)) {
                            fallbackPrefix = getParentPrefix(prefix);
                        }

                        if (fallbackPrefix != null
                                && !Objects.equals(fallbackPrefix, prefix)) {
                            treeController.selectPrefix(fallbackPrefix);
                        }

                        JOptionPane.showMessageDialog(
                                this,
                                "The folder no longer exists:\n" + prefix,
                                "Folder Not Found",
                                JOptionPane.ERROR_MESSAGE);

                        return;
                    }

                    completeTreeSelection(bucket, prefix);
                }));
    }

    private void completeTreeSelection(
            String bucket,
            String prefix) {

        /*
         * The tree selection has been validated at this point.
         * Avoid disabling Flat Browsing for a selection that resolves
         * to the location already displayed in the file table.
         */
        boolean locationChanged =
                !Objects.equals(bucket, currentFileBucket)
                        || !Objects.equals(
                        prefix,
                        currentFilePrefix);

        if (locationChanged) {
            prepareForLocationChange();
        }

        loadFiles(
                bucket,
                prefix,
                true);

        updateBreadcrumb(prefix);

        if (view.isLocationEditMode()) {
            view.getLocationField().setText(
                    prefix == null ? "" : prefix);
        }

        NavigationEntry restoreTarget =
                pendingNavigationTarget;

        if (navigationPlayback
                && restoreTarget != null
                && Objects.equals(
                restoreTarget.repositoryId(),
                getCurrentRepository() == null
                        ? null
                        : getCurrentRepository().getId())
                && Objects.equals(
                restoreTarget.bucket(),
                bucket)
                && Objects.equals(
                restoreTarget.prefix(),
                prefix)) {

            pendingNavigationTarget = null;
            pendingNavigationPreviousIndex = -1;
            navigationPlayback = false;

        } else {
            recordNavigation();
        }

        updateNavigationButtonStates();
        updateActionStates();
    }

    private void recoverMissingHistoryTarget(
            NavigationEntry target) {

        // Bu servis, hedef repository'nin S3 bağlantısını kullanmalı.
        S3ExplorerService service = getService();

        CompletableFuture.supplyAsync(() -> {

            /*
             * Başlangıçta doğrulanamayan hedef klasör silinmiş
             * kabul edilir. Üst klasörleri kontrol ederek silinen
             * en üst seviyeyi ve mevcut en yakın üst klasörü bul.
             */
            String invalidPrefix = target.prefix();
            String candidate = getParentPrefix(target.prefix());

            while (candidate != null && !candidate.isBlank()) {

                boolean parentExists = Boolean.TRUE.equals(
                        service.folderExists(
                                target.bucket(),
                                candidate));

                if (parentExists) {
                    break;
                }

                invalidPrefix = candidate;
                candidate = getParentPrefix(candidate);
            }

            String fallbackPrefix =
                    candidate == null
                            ? S3TreeNode.ROOT_PREFIX
                            : candidate;

            return new String[]{
                    invalidPrefix,
                    fallbackPrefix
            };

        }, explorerPool).whenComplete((recovery, exception) ->
                SwingUtilities.invokeLater(() -> {

                    // Bu sırada kullanıcı başka bir konuma geçtiyse
                    // eski doğrulamanın sonucunu uygulama.
                    if (!Objects.equals(
                            pendingNavigationTarget,
                            target)) {
                        return;
                    }

                    if (exception != null) {

                        navigationHistoryIndex =
                                pendingNavigationPreviousIndex;

                        pendingNavigationTarget = null;
                        pendingNavigationPreviousIndex = -1;
                        navigationPlayback = false;

                        updateNavigationButtonStates();

                        log.error(
                                "[HISTORY RECOVERY] failed bucket={} prefix={}",
                                target.bucket(),
                                target.prefix(),
                                exception);

                        JOptionPane.showMessageDialog(
                                this,
                                "The folder hierarchy could not be verified.",
                                "Navigation History",
                                JOptionPane.ERROR_MESSAGE);

                        return;
                    }

                    String invalidPrefix = recovery[0];
                    String fallbackPrefix = recovery[1];

                    log.info(
                            "[HISTORY RECOVERY] target={} invalidPrefix={} "
                                    + "fallbackPrefix={}",
                            target.prefix(),
                            invalidPrefix,
                            fallbackPrefix);

                    // Eksik klasörün tamamını ağaçtan kaldır.
                    treeController.removePrefix(invalidPrefix);

                    // Aynı klasör ve altındaki geçmiş kayıtlarını kaldır.
                    removeHistoryEntriesForPrefix(
                            target.repositoryId(),
                            target.bucket(),
                            invalidPrefix);

                    NavigationEntry fallbackEntry =
                            new NavigationEntry(
                                    target.repositoryId(),
                                    target.bucket(),
                                    fallbackPrefix);

                    int fallbackIndex =
                            navigationHistory.indexOf(fallbackEntry);

                    if (fallbackIndex < 0) {

                        /*
                         * Normal akışta fallback kaydı geçmişte bulunur.
                         * Bulunamazsa mevcut konumu zorla değiştirmeden
                         * bekleyen navigasyonu sonlandır.
                         */
                        pendingNavigationTarget = null;
                        pendingNavigationPreviousIndex = -1;
                        navigationPlayback = false;

                        updateNavigationButtonStates();

                        JOptionPane.showMessageDialog(
                                this,
                                "The nearest existing folder was not found "
                                        + "in navigation history.",
                                "Navigation History",
                                JOptionPane.ERROR_MESSAGE);

                        return;
                    }

                    /*
                     * İleri geçmişini silmeden indeksi mevcut üst klasöre
                     * taşı. Repository 2 kaydı geçmişte korunur.
                     */
                    navigationHistoryIndex = fallbackIndex;
                    pendingNavigationPreviousIndex = -1;
                    pendingNavigationTarget = fallbackEntry;
                    navigationPlayback = true;

                    contentLoader.invalidate(
                            target.bucket(),
                            fallbackPrefix);

                    treeController.selectPrefix(fallbackPrefix);

                    String message =
                            "The folder in navigation history no longer exists:\n"
                                    + target.prefix()
                                    + "\n\nNavigated to the first existing location "
                                    + "in navigation history:\n"
                                    + (fallbackPrefix.isBlank()
                                    ? "/"
                                    : fallbackPrefix);

                    JOptionPane.showMessageDialog(
                            this,
                            message,
                            "Navigation History",
                            JOptionPane.WARNING_MESSAGE);

                    updateNavigationButtonStates();
                }));
    }

    private void recoverMissingReloadFolder(
            S3ExplorerService service,
            String repositoryId,
            String bucket,
            String missingPrefix) {

        CompletableFuture.supplyAsync(() -> {

            String invalidPrefix = missingPrefix;
            String candidate = getParentPrefix(missingPrefix);

            while (candidate != null && !candidate.isBlank()) {

                if (service.folderExists(bucket, candidate)) {
                    break;
                }

                invalidPrefix = candidate;
                candidate = getParentPrefix(candidate);
            }

            String fallbackPrefix = candidate == null
                    ? S3TreeNode.ROOT_PREFIX
                    : candidate;

            return new String[]{invalidPrefix, fallbackPrefix};

        }, explorerPool).whenComplete((result, exception) ->
                SwingUtilities.invokeLater(() -> {

                    RepositoryDefinition active = getCurrentRepository();

                    if (active == null
                            || !Objects.equals(active.getId(), repositoryId)
                            || !Objects.equals(getCurrentBucket(), bucket)
                            || !Objects.equals(getCurrentPrefix(), missingPrefix)) {
                        return;
                    }

                    if (exception != null) {
                        log.error(
                                "[RELOAD FOLDER] recovery failed bucket={} prefix={}",
                                bucket, missingPrefix, exception);

                        JOptionPane.showMessageDialog(
                                this,
                                S3ErrorResolver.getUserMessage(exception),
                                "Folder Reload Failed",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    String invalidPrefix = result[0];
                    String fallbackPrefix = result[1];

                    log.info(
                            "[RELOAD FOLDER] missingPrefix={} invalidPrefix={} fallbackPrefix={}",
                            missingPrefix, invalidPrefix, fallbackPrefix);

                    treeController.removePrefix(invalidPrefix);

                    removeHistoryEntriesForPrefix(
                            repositoryId,
                            bucket,
                            invalidPrefix);

                    contentLoader.invalidate(bucket, fallbackPrefix);

                    treeController.refreshNode(
                            new RefreshTreeNode(
                                    fallbackPrefix,
                                    RefreshTreeOperation.REFRESH));

                    treeController.selectPrefix(fallbackPrefix);

                    JOptionPane.showMessageDialog(
                            this,
                            "The current folder no longer exists:\n"
                                    + missingPrefix
                                    + "\n\nNavigated to the nearest existing folder:\n"
                                    + (fallbackPrefix.isBlank()
                                    ? "/"
                                    : fallbackPrefix),
                            "Folder Reload",
                            JOptionPane.WARNING_MESSAGE);
                }));
    }

    private void selectAllFiles() {
        JTable table = view.getFileTable();

        if (table == null || table.getRowCount() == 0) {
            return;
        }

        table.selectAll();
        updateActionStates();
    }

    private void goHome() {
        if (getCurrentBucket() == null || treeController == null) {
            return;
        }

        treeController.selectPrefix(S3TreeNode.ROOT_PREFIX);
    }

    private void clearNavigationHistory() {
        navigationHistory.clear();
        navigationHistoryIndex = -1;

        updateNavigationButtonStates();
    }

    private JMenuItem createMenuItem(Action action, String shortcut) {
        JMenuItem menuItem = new JMenuItem(action);

        if (shortcut != null) {
            menuItem.setAccelerator(KeyStroke.getKeyStroke(shortcut));
        }

        return menuItem;
    }

    public void populateFileMenu(JMenu menu) {
        menu.add(createMenuItem(propertiesAction, "alt ENTER"));
        menu.addSeparator();

        menu.add(new JMenuItem(newFolderAction));
        menu.add(createMenuItem(deleteAction, "DELETE"));
        menu.add(createMenuItem(renameAction, "F2"));
        menu.addSeparator();

        menu.add(new JMenuItem(uploadAction));

        uploadEncryptedMenuItem = new JMenuItem(uploadEncryptedAction);
        menu.add(uploadEncryptedMenuItem);

        menu.add(new JMenuItem(downloadAction));

        downloadDecryptedMenuItem = new JMenuItem(downloadDecryptedAction);
        menu.add(downloadDecryptedMenuItem);

        menu.add(new JMenuItem(bulkDownloadAction));

        menu.addSeparator();
        menu.add(new JMenuItem(exportFileListAction));

        updateEncryptionMenuVisibility();
    }

    public void populateEditMenu(JMenu menu) {
        menu.add(createMenuItem(cutAction, "control X"));
        menu.add(createMenuItem(copyAction, "control C"));
        menu.add(createMenuItem(pasteAction, "control V"));
        menu.add(createMenuItem(selectAllAction, "control A"));
    }

    public void populateViewMenu(JMenu menu) {
        menu.add(new JMenuItem(refreshAction));
        menu.add(createMenuItem(reloadFolderAction, "F5"));
    }

    public void populateGoMenu(JMenu menu) {
        menu.add(createMenuItem(goToParentAction, "BACK_SPACE"));
        menu.add(createMenuItem(backAction, "alt LEFT"));
        menu.add(createMenuItem(forwardAction, "alt RIGHT"));
        menu.add(new JMenuItem(homeAction));
        menu.addSeparator();

        menu.add(new JMenuItem(clearHistoryAction));
    }

    private void updateEncryptionMenuVisibility() {
        boolean configured = hasEncryptionConfiguration();

        if (uploadEncryptedMenuItem != null) {
            uploadEncryptedMenuItem.setVisible(configured);
        }

        if (downloadDecryptedMenuItem != null) {
            downloadDecryptedMenuItem.setVisible(configured);
        }
    }

    public void setMenuIconUpdater(Runnable updater) {
        themeManager.setMenuIconUpdater(updater);
    }

    private void toggleFlatBrowsing() {

        flatBrowsingEnabled = !flatBrowsingEnabled;

        // A change of browsing mode starts a new discovery session.
        fileDiscoveryGeneration.incrementAndGet();

        if (view != null) {
            view.setFlatBrowsingEnabled(flatBrowsingEnabled);
        }

        reloadCurrentFileTable();
    }

    private String getFlatDisplayName(
            String key,
            String prefix,
            boolean folder) {

        String effectivePrefix =
                prefix == null ? "" : prefix;

        String displayName =
                key.startsWith(effectivePrefix)
                        ? key.substring(effectivePrefix.length())
                        : key;

        if (folder && displayName.endsWith("/")) {
            displayName =
                    displayName.substring(
                            0,
                            displayName.length() - 1);
        }

        return displayName;
    }


    private void exportFileList() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export List");
        chooser.setSelectedFile(new File("file-list.csv"));

        if (lastOpenedFolderToExport != null
                && lastOpenedFolderToExport.isDirectory()) {
            chooser.setCurrentDirectory(lastOpenedFolderToExport);
        }

        int result = chooser.showSaveDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();

        if (!selectedFile.getName()
                .toLowerCase(Locale.ROOT)
                .endsWith(".csv")) {
            selectedFile = new File(
                    selectedFile.getParentFile(),
                    selectedFile.getName() + ".csv");
        }

        final File exportFile = selectedFile;
        final Path exportPath = exportFile.toPath();

        if (Files.exists(exportPath)) {
            int overwrite = JOptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "The selected file already exists. Overwrite it?",
                    "Confirm Export",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        File parentFolder =
                exportFile.getAbsoluteFile().getParentFile();

        if (parentFolder != null) {
            lastOpenedFolderToExport = parentFolder;
        }

        final String bucket = currentFileBucket;
        final String prefix = currentFilePrefix == null
                ? S3TreeNode.ROOT_PREFIX
                : currentFilePrefix;

        if (bucket == null || bucket.isBlank()) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "There is no active bucket to export.",
                    "Export List",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        final FileTableSortSpec sortSpec =
                getCurrentFileSortSpec();

        /*
         * Export the complete folder content, independently of the
         * row limit used by the visible file table.
         */
        contentLoader.loadFolder(
                        explorerPool,
                        bucket,
                        prefix,
                        Integer.MAX_VALUE,
                        sortSpec,
                        null,
                        flatBrowsingEnabled)
                .thenAccept(content -> {
                    try {
                        FileTableModel exportModel =
                                new FileTableModel();

                        List<S3FileItem> exportItems =
                                new java.util.ArrayList<>();

                        String repositoryId =
                                getCurrentRepository().getId();

                        /*
                         * Add folders first, using the same S3FileItem
                         * representation as the visible file table.
                         */
                        exportItems.addAll(
                                content.folders()
                                        .stream()
                                        .map(folderKey ->
                                                new S3FileItem(
                                                        repositoryId,
                                                        bucket,
                                                        folderKey,
                                                        0,
                                                        null,
                                                        null,
                                                        true,
                                                        flatBrowsingEnabled
                                                                ? getFlatDisplayName(
                                                                folderKey,
                                                                prefix,
                                                                true)
                                                                : null))
                                        .toList());

                        /*
                         * Add files. S3 directory-marker objects ending
                         * with "/" are excluded, matching the normal
                         * file-table loading behavior.
                         */
                        exportItems.addAll(
                                content.files()
                                        .stream()
                                        .filter(object ->
                                                !object.key().endsWith("/"))
                                        .map(object ->
                                                new S3FileItem(
                                                        repositoryId,
                                                        bucket,
                                                        object.key(),
                                                        object.size(),
                                                        object.lastModified(),
                                                        object.storageClass() == null
                                                                ? null
                                                                : object.storageClass().toString(),
                                                        false,
                                                        flatBrowsingEnabled
                                                                ? getFlatDisplayName(
                                                                object.key(),
                                                                prefix,
                                                                false)
                                                                : null))
                                        .toList());

                        /*
                         * Apply the same filters used by the visible file table.
                         * Export all matching items, regardless of the visible row limit.
                         */
                        String filterText = view.getFilterText();

                        if (filterText == null) {
                            filterText = "";
                        }

                        Locale turkishLocale =
                                Locale.forLanguageTag("tr-TR");

                        String normalizedFilter =
                                filterText.trim().toLowerCase(turkishLocale);

                        String itemTypeFilter =
                                view.getItemTypeFilter();

                        boolean foldersOnly =
                                "Folders Only".equals(itemTypeFilter);

                        boolean filesOnly =
                                "Files Only".equals(itemTypeFilter);

                        exportItems.removeIf(item -> {

                            if (foldersOnly && !item.isFolder()) {
                                return true;
                            }

                            if (filesOnly && !item.isFile()) {
                                return true;
                            }

                            if (item.isParentFolder()) {
                                return true;
                            }

                            if (normalizedFilter.isEmpty()) {
                                return false;
                            }

                            String itemName = item.getDisplayName();

                            return itemName == null
                                    || !itemName
                                    .toLowerCase(turkishLocale)
                                    .contains(normalizedFilter);
                        });

                        exportModel.setFiles(exportItems);

                        JTable exportTable =
                                new JTable(exportModel);

                        FileTableRowSorter exportSorter =
                                new FileTableRowSorter(exportModel);

                        exportTable.setRowSorter(exportSorter);

                        /*
                         * Reuse the current table's sort keys so that
                         * folders and files follow the visible table's
                         * sorting rules.
                         */
                        if (view.getFileTable().getRowSorter()
                                instanceof FileTableRowSorter currentSorter) {
                            exportSorter.setSortKeys(
                                    currentSorter.getSortKeys());
                        }

                        try (java.io.BufferedWriter writer =
                                     Files.newBufferedWriter(
                                             exportPath,
                                             java.nio.charset.StandardCharsets.UTF_8)) {

                            // UTF-8 BOM helps Excel recognize UTF-8 CSV.
                            writer.write('\uFEFF');

                            writeCsvRow(
                                    writer,
                                    "Name",
                                    "Type",
                                    "Size",
                                    "Last Modified",
                                    "Storage Class");

                            /*
                             * Export in sorted JTable view order.
                             * Do not apply the visible table's row limit.
                             */
                            for (int viewRow = 0;
                                 viewRow < exportTable.getRowCount();
                                 viewRow++) {

                                int modelRow =
                                        exportTable.convertRowIndexToModel(
                                                viewRow);

                                S3FileItem item =
                                        exportModel.getItem(modelRow);

                                if (item.isParentFolder()) {
                                    continue;
                                }

                                writeCsvRow(
                                        writer,
                                        item.getKey(),
                                        exportModel.getValueAt(
                                                modelRow,
                                                FileTableModel.COL_TYPE),
                                        exportModel.getValueAt(
                                                modelRow,
                                                FileTableModel.COL_SIZE),
                                        item.getLastModified() == null
                                                ? ""
                                                : EXPORT_DATETIME_FORMATTER.format(
                                                item.getLastModified()
                                                        .atZone(
                                                                ZoneId.systemDefault())),
                                        exportModel.getValueAt(
                                                modelRow,
                                                FileTableModel.COL_STORAGE_CLASS));
                            }
                        }

                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(
                                        SwingUtilities.getWindowAncestor(this),
                                        "List exported successfully.",
                                        "Export List",
                                        JOptionPane.INFORMATION_MESSAGE));

                    } catch (IOException e) {
                        log.error(
                                "Failed to export list to {}",
                                exportPath,
                                e);

                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(
                                        SwingUtilities.getWindowAncestor(this),
                                        "Failed to export list:\n"
                                                + e.getMessage(),
                                        "Export Error",
                                        JOptionPane.ERROR_MESSAGE));
                    }
                })
                .exceptionally(ex -> {
                    log.error(
                            "Failed to load complete list for export. "
                                    + "Bucket={}, prefix={}",
                            bucket,
                            prefix,
                            ex);

                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(
                                    SwingUtilities.getWindowAncestor(this),
                                    "Failed to load the complete list:\n"
                                            + S3ErrorResolver.getUserMessage(ex),
                                    "Export Error",
                                    JOptionPane.ERROR_MESSAGE));

                    return null;
                });
    }

    private void writeCsvRow(
            java.io.BufferedWriter writer,
            Object... values) throws IOException {

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }

            writer.write('"');

            if (values[i] != null) {
                writer.write(
                        String.valueOf(values[i]).replace("\"", "\"\""));
            }

            writer.write('"');
        }

        writer.newLine();
    }

    private String formatCount(
            long count,
            String singular) {

        return S3Util.formatWithThousandSeparator(count)
                + " "
                + singular
                + (count == 1 ? "" : "s");
    }


    private List<S3FileItem> getSelectedFileItems() {
        JTable table = view.getFileTable();
        int[] selectedViewRows = table.getSelectedRows();

        List<S3FileItem> selectedItems =
                new ArrayList<>(selectedViewRows.length);

        for (int viewRow : selectedViewRows) {
            int modelRow =
                    table.convertRowIndexToModel(viewRow);

            S3FileItem item =
                    view.getFileTableModel().getItem(modelRow);

            if (item == null || item.isParentFolder()) {
                continue;
            }

            selectedItems.add(item);
        }

        return selectedItems;
    }

    private void applyServerFiltersIfChanged() {

        String currentText = view.getFilterText();
        String currentItemType = view.getItemTypeFilter();

        if (currentText == null) {
            currentText = "";
        }

        if (currentItemType == null) {
            currentItemType = "All";
        }

        if (currentText.equals(lastAppliedFilterText)
                && currentItemType.equals(lastAppliedItemType)) {
            return;
        }

        lastAppliedFilterText = currentText;
        lastAppliedItemType = currentItemType;

        reloadCurrentFileTable(true);
    }

    private record PreparedFileTable(
            LimitedFolderContent content,
            List<S3FileItem> rows) {
    }

    private int confirmUploadConflict(
            String bucket,
            String objectKey) {

        if (!getService().objectExists(bucket, objectKey)) {
            return 3;
        }

        String[] options = {
                "Overwrite",
                "Skip",
                "Cancel"
        };

        int result = JOptionPane.showOptionDialog(
                SwingUtilities.getWindowAncestor(this),
                objectKey + " already exists.\n"
                        + "Do you want to overwrite it?",
                "File Conflict",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]);

        // 0 = Overwrite, 1 = Skip, 2 or close = Cancel
        return result;
    }

    private void uploadFile() {

        RepositoryDefinition repository =
                getCurrentRepository();

        String bucket =
                getCurrentBucket();

        if (repository == null
                || repository.isEmpty()
                || bucket == null
                || bucket.isBlank()) {
            return;
        }

        String repositoryId = repository.getId();

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(
                JFileChooser.FILES_AND_DIRECTORIES);

        if (lastOpenedFolderToUpload != null) {
            chooser.setCurrentDirectory(
                    lastOpenedFolderToUpload);
        }

        int result = chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String prefix =
                currentFilePrefix == null
                        ? S3TreeNode.ROOT_PREFIX
                        : currentFilePrefix;

        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }

        File[] selectedFiles = chooser.getSelectedFiles();

        List<Path> uploadPaths = new ArrayList<>();
        List<String> objectKeys = new ArrayList<>();
        List<Boolean> overwrites = new ArrayList<>();
        List<File> selectedDirectories = new ArrayList<>();

        /*
         * First collect upload decisions.
         * If the user cancels a conflict prompt, do not submit
         * a partial file group.
         */
        for (File file : selectedFiles) {
            if (file.isDirectory()) {
                selectedDirectories.add(file);
                continue;
            }

            if (!file.isFile()) {
                continue;
            }

            String objectKey =
                    prefix + file.getName();

            try {
                int conflictResult =
                        confirmUploadConflict(
                                bucket,
                                objectKey);

                if (conflictResult == 1) {
                    log.info(
                            "[UPLOAD GROUP] skipped existing object key={}",
                            objectKey);
                    continue;
                }

                if (conflictResult != 0
                        && conflictResult != 3) {
                    log.info(
                            "[UPLOAD GROUP] selection cancelled at key={}",
                            objectKey);
                    return;
                }

                uploadPaths.add(file.toPath());
                objectKeys.add(objectKey);
                overwrites.add(conflictResult == 0);

            } catch (Exception ex) {
                log.error(
                        "[UPLOAD GROUP] conflict check failed key={}",
                        objectKey,
                        ex);

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Upload Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        /*
         * Preserve the existing folder-upload workflow.
         * Folder uploads already create their own UPLOAD_GROUP.
         */
        for (File directory : selectedDirectories) {
            try {
                String folderKey =
                        S3Util.combineKey(
                                prefix,
                                directory.getName()) + "/";

                lastOpenedFolderToUpload = directory;

                log.info(
                        "[UPLOAD FOLDER] targetBucket={} targetPrefix={} selectionKey={}",
                        bucket,
                        prefix,
                        folderKey);

                transferManager.submitFolderUpload(
                        repositoryId,
                        bucket,
                        prefix,
                        directory.toPath());

            } catch (Exception ex) {
                log.error(
                        "[UPLOAD FOLDER] submission failed folder={}",
                        directory.getAbsolutePath(),
                        ex);

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Upload Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        /*
         * Submit all selected regular files as one Upload Group.
         */
        if (!uploadPaths.isEmpty()) {
            try {
                pendingFileTableSelectionKey = null;
                pendingFileTableSelectionKeys = null;
                restoreFileTableFocus = false;

                for (Path path : uploadPaths) {
                    lastOpenedFolderToUpload =
                            path.toFile().getParentFile();
                }

                log.info(
                        "[UPLOAD GROUP] submitting files={} bucket={} prefix={} keys={}",
                        uploadPaths.size(),
                        bucket,
                        prefix,
                        objectKeys);

                transferManager.submitUploadGroup(
                        repositoryId,
                        bucket,
                        prefix,
                        uploadPaths,
                        objectKeys,
                        overwrites);

            } catch (Exception ex) {
                pendingFileTableSelectionKey = null;
                restoreFileTableFocus = false;

                log.error(
                        "[UPLOAD GROUP] submission failed bucket={} prefix={}",
                        bucket,
                        prefix,
                        ex);

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Upload Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private void uploadFileEncrypted() {

        RepositoryDefinition repository =
                getCurrentRepository();

        String bucket =
                getCurrentBucket();

        if (repository == null
                || repository.isEmpty()
                || bucket == null
                || bucket.isBlank()
                || encryptionConfig == null) {
            return;
        }

        String repositoryId = repository.getId();

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(
                JFileChooser.FILES_ONLY);

        if (lastOpenedFolderToUpload != null) {
            chooser.setCurrentDirectory(
                    lastOpenedFolderToUpload);
        }

        int result = chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String prefix =
                currentFilePrefix == null
                        ? S3TreeNode.ROOT_PREFIX
                        : currentFilePrefix;

        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix += "/";
        }

        File[] selectedFiles = chooser.getSelectedFiles();

        List<Path> uploadPaths = new ArrayList<>();
        List<String> objectKeys = new ArrayList<>();
        List<Boolean> overwrites = new ArrayList<>();

        /*
         * Resolve all conflict decisions before submitting the group.
         */
        for (File file : selectedFiles) {
            if (!file.isFile()) {
                continue;
            }

            String objectKey =
                    prefix + file.getName();

            try {
                int conflictResult =
                        confirmUploadConflict(
                                bucket,
                                objectKey);

                if (conflictResult == 1) {
                    log.info(
                            "[UPLOAD ENCRYPTED GROUP] skipped existing object key={}",
                            objectKey);
                    continue;
                }

                if (conflictResult != 0
                        && conflictResult != 3) {
                    log.info(
                            "[UPLOAD ENCRYPTED GROUP] selection cancelled at key={}",
                            objectKey);
                    return;
                }

                uploadPaths.add(file.toPath());
                objectKeys.add(objectKey);
                overwrites.add(conflictResult == 0);

            } catch (Exception ex) {
                log.error(
                        "[UPLOAD ENCRYPTED GROUP] conflict check failed key={}",
                        objectKey,
                        ex);

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Encrypted Upload Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (uploadPaths.isEmpty()) {
            return;
        }

        try {
            pendingFileTableSelectionKey = null;
            pendingFileTableSelectionKeys = null;
            restoreFileTableFocus = false;

            for (Path path : uploadPaths) {
                lastOpenedFolderToUpload =
                        path.toFile().getParentFile();
            }

            log.info(
                    "[UPLOAD ENCRYPTED GROUP] submitting files={} bucket={} prefix={} keys={}",
                    uploadPaths.size(),
                    bucket,
                    prefix,
                    objectKeys);

            transferManager.submitUploadEncryptedGroup(
                    repositoryId,
                    bucket,
                    prefix,
                    uploadPaths,
                    objectKeys,
                    overwrites,
                    encryptionConfig);

        } catch (Exception ex) {
            pendingFileTableSelectionKey = null;
            restoreFileTableFocus = false;

            log.error(
                    "[UPLOAD ENCRYPTED GROUP] submission failed bucket={} prefix={}",
                    bucket,
                    prefix,
                    ex);

            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Encrypted Upload Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Invalidates pending file-table loads when the active location changes
     * and disables Flat Browsing for the new location.
     *
     * This method does not start a file-table reload. The caller is
     * responsible for continuing the existing navigation flow.
     */
    private void prepareForLocationChange() {

        // Invalidate progress updates from the previous location session.
        fileDiscoveryGeneration.incrementAndGet();

        /*
         * Invalidate the active folder's snapshot when Flat Browsing
         * is enabled. This prevents a later visit from joining an
         * in-flight scan whose progress listener belongs to an
         * obsolete location session.
         */
        if (flatBrowsingEnabled
                && currentFileBucket != null
                && currentFilePrefix != null) {

            contentLoader.invalidate(
                    currentFileBucket,
                    currentFilePrefix);
        }

        // Reject any pending file-table result for the old location.
        fileLoadGeneration.incrementAndGet();

        if (flatBrowsingEnabled) {
            flatBrowsingEnabled = false;

            if (view != null) {
                view.setFlatBrowsingEnabled(false);
            }
        }
    }

    private void reloadCurrentFileTableAfterSort() {
        if (!currentFileTableLimitReached) {
            if (view.getFileTable().getRowSorter()
                    instanceof FileTableRowSorter sorter) {
                sorter.applyPendingSortKeys();
                view.getFileTable().getTableHeader().repaint();
            }

            return;
        }

        reloadCurrentFileTable();
    }
}
