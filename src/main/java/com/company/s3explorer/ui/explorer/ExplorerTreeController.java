package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public final class ExplorerTreeController {

    private static final Logger log =
            LoggerFactory.getLogger(ExplorerTreeController.class);

    private final JTree folderTree;
    private final DefaultTreeModel treeModel;
    private final ExplorerContentLoader contentLoader;
    private final Supplier<ExecutorService> explorerPoolSupplier;
    private final Supplier<String> currentBucketSupplier;

    /*
     * Tree state artık ExplorerPanel'de değil.
     */
    private final Map<String, S3TreeNode> nodeCache =
            new HashMap<>();

    private final Map<S3TreeNode, Long> treeLoadGenerations =
            new IdentityHashMap<>();

    private volatile String selectedPrefix =
            S3TreeNode.ROOT_PREFIX;

    public ExplorerTreeController(
            JTree folderTree,
            DefaultTreeModel treeModel,
            ExplorerContentLoader contentLoader,
            Supplier<ExecutorService> explorerPoolSupplier,
            Supplier<String> currentBucketSupplier) {

        this.folderTree = Objects.requireNonNull(folderTree);
        this.treeModel = Objects.requireNonNull(treeModel);
        this.contentLoader = Objects.requireNonNull(contentLoader);
        this.explorerPoolSupplier =
                Objects.requireNonNull(
                        explorerPoolSupplier);
        this.currentBucketSupplier =
                Objects.requireNonNull(currentBucketSupplier);

        initializeRoot();
    }

    /*
     * ---------------------------------------------------------
     * ROOT
     * ---------------------------------------------------------
     */

    public void initializeRoot() {

        S3TreeNode root =
                new S3TreeNode(
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX);

        treeModel.setRoot(root);

        treeLoadGenerations.clear();
        nodeCache.clear();

        nodeCache.put(
                root.getFullPrefix(),
                root);
        
        selectedPrefix =
                S3TreeNode.ROOT_PREFIX;
        
        treeModel.reload();

        folderTree.clearSelection();
    }

    /**
     * Bucket değiştiğinde root'a gelen klasörleri uygular.
     * Burada yalnızca Tree güncellenir.
     * File Table yüklenmez.
     */
    public void applyRootFolders(
            String bucket,
            List<String> folders) {

        if (bucket == null || bucket.isBlank()) {
            return;
        }

        S3TreeNode root =
                new S3TreeNode(
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX,
                        S3TreeNode.ROOT_PREFIX);

        treeLoadGenerations.clear();
        nodeCache.clear();

        selectedPrefix =
                S3TreeNode.ROOT_PREFIX;
        
        nodeCache.put(
                root.getFullPrefix(),
                root);

        if (folders != null) {

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

                /*
                 * Lazy-loading marker.
                 */
                child.add(
                        new S3TreeNode(
                                S3TreeNode.LOADING,
                                bucket,
                                folder));

                root.add(child);
            }
        }

        treeModel.setRoot(root);
        treeModel.reload();

        /*
         * Root seçimi mevcut davranışla aynı.
         */
        folderTree.setSelectionRow(0);
    }

    /*
     * ---------------------------------------------------------
     * CHILDREN
     * ---------------------------------------------------------
     */

    public void loadChildren(
            S3TreeNode parentNode) {

        loadChildren(parentNode, false);
    }

    public void refreshNode(
            RefreshTreeNode request) {

        if (request == null) {
            return;
        }

        String prefix =
                request.prefix();

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
                node == null
                        ? -1
                        : node.getChildCount());

        if (node == null) {

            log.debug(
                    "[EXPLORER TREE REFRESH NODE] NODE NOT FOUND");

            return;
        }

        loadChildren(node, true);
    }

    private void loadChildren(
            S3TreeNode parentNode,
            boolean forceRefresh) {

        loadChildrenAsync(
                parentNode,
                forceRefresh);
    }

    private CompletableFuture<Void> loadChildrenAsync(
            S3TreeNode parentNode,
            boolean forceRefresh) {

        if (parentNode == null) {
            return CompletableFuture.completedFuture(null);
        }

        final String bucket =
                currentBucketSupplier.get();

        if (bucket == null
                || bucket.isBlank()) {

            return CompletableFuture.completedFuture(null);
        }

        final String prefix =
                parentNode.getFullPrefix();

        final long generation =
                treeLoadGenerations.merge(
                        parentNode,
                        1L,
                        Long::sum);

        /*
         * Already loaded.
         *
         * Normal Tree expansion does not reload
         * an already discovered node.
         */
        if (!forceRefresh
                && parentNode.getChildCount() > 0) {

            Object firstChild =
                    parentNode.getChildAt(0);

            if (firstChild instanceof S3TreeNode child
                    && !child.isLoading()) {

                return CompletableFuture.completedFuture(null);
            }
        }

        /*
         * Lazy-loading placeholder.
         */
        parentNode.removeAllChildren();

        parentNode.add(
                new S3TreeNode(
                        S3TreeNode.LOADING,
                        bucket,
                        prefix));
        log.info(
                "[TREE MODEL RELOAD] parent={} childCount={} selectedPath={}",
                parentNode.getFullPrefix(),
                parentNode.getChildCount(),
                folderTree.getSelectionPath());
        treeModel.reload(parentNode);

        ExecutorService currentPool =
                explorerPoolSupplier.get();

        if (currentPool == null
                || currentPool.isShutdown()
                || currentPool.isTerminated()) {

            log.warn(
                    "[TREE LOAD] explorer pool unavailable - bucket={} prefix={}",
                    bucket,
                    prefix);

            return CompletableFuture.completedFuture(null);
        }

        log.debug(
                "[TREE LOAD] bucket={} prefix={} forceRefresh={}",
                bucket,
                prefix,
                forceRefresh);

        return contentLoader.loadFolders(
                        currentPool,
                        bucket,
                        prefix,
                        null)
                .thenCompose(content -> {

                    CompletableFuture<Void> applied =
                            new CompletableFuture<>();

                    SwingUtilities.invokeLater(() -> {

                        try {

                            Long currentGeneration =
                                    treeLoadGenerations.get(
                                            parentNode);

                            if (!Objects.equals(
                                    generation,
                                    currentGeneration)) {

                                applied.complete(null);
                                return;
                            }

                            if (parentNode.getParent() == null
                                    && parentNode
                                    != treeModel.getRoot()) {

                                applied.complete(null);
                                return;
                            }

                            parentNode.removeAllChildren();

                            for (String folder :
                                    content.folders()) {

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
                                                folder));

                                parentNode.add(child);
                            }

                            TreePath selectionBeforeReload =
                                    folderTree.getSelectionPath();

                            log.info(
                                    "[TREE MODEL RELOAD] parent={} childCount={} selectedPath={}",
                                    parentNode.getFullPrefix(),
                                    parentNode.getChildCount(),
                                    selectionBeforeReload);

                            treeModel.reload(parentNode);

                            if (selectionBeforeReload != null) {

                                SwingUtilities.invokeLater(() ->
                                        restoreSelection(
                                                selectionBeforeReload));
                            }

                            /*
                             * ÖNEMLİ:
                             *
                             * Tree child'larının gerçekten model'e
                             * uygulanmasından sonra Future tamamlanıyor.
                             */
                            applied.complete(null);

                        } catch (Exception ex) {

                            applied.completeExceptionally(ex);
                        }
                    });

                    return applied;
                });
    }

    private void restoreSelection(
            TreePath selectionPath) {

        if (selectionPath == null) {
            return;
        }

        Object last =
                selectionPath.getLastPathComponent();

        if (!(last instanceof S3TreeNode selectedNode)) {
            return;
        }

        S3TreeNode restoredNode =
                findNodeByPrefix(
                        selectedNode.getFullPrefix());

        if (restoredNode == null) {
            return;
        }

        TreePath restoredPath =
                new TreePath(
                        restoredNode.getPath());

        folderTree.setSelectionPath(
                restoredPath);

        folderTree.scrollPathToVisible(
                restoredPath);

        log.info(
                "[TREE SELECTION RESTORE] prefix={} selected={} path={}",
                restoredNode.getFullPrefix(),
                folderTree.isPathSelected(
                        restoredPath),
                restoredPath);
    }
    
    /*
     * ---------------------------------------------------------
     * NODE LOOKUP
     * ---------------------------------------------------------
     */

    public S3TreeNode getSelectedNode() {

        return (S3TreeNode)
                folderTree.getLastSelectedPathComponent();
    }

    public String getSelectedPrefix() {

        return selectedPrefix;
    }

    public S3TreeNode findNodeByPrefix(
            String prefix) {

        if (prefix == null) {
            return null;
        }

        /*
         * First use the cache.
         *
         * This is the normal/fast path.
         */
        S3TreeNode cached =
                nodeCache.get(prefix);

        if (cached != null) {
            return cached;
        }

        /*
         * Fallback to tree traversal.
         */
        S3TreeNode root =
                (S3TreeNode) treeModel.getRoot();

        if (root == null) {
            return null;
        }

        return findNodeRecursive(
                root,
                prefix);
    }

    private S3TreeNode findNodeRecursive(
            S3TreeNode root,
            String prefix) {

        if (prefix.equals(
                root.getFullPrefix())) {

            return root;
        }

        for (int i = 0;
             i < root.getChildCount();
             i++) {

            S3TreeNode child =
                    (S3TreeNode)
                            root.getChildAt(i);

            if (prefix.startsWith(
                    child.getFullPrefix())) {

                S3TreeNode result =
                        findNodeRecursive(
                                child,
                                prefix);

                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /*
     * ---------------------------------------------------------
     * NAVIGATION
     * ---------------------------------------------------------
     */
    private void selectNode(S3TreeNode node) {

        if (node == null) {
            return;
        }

        TreePath path =
                new TreePath(node.getPath());

        log.info(
                "[TREE SELECT NODE] prefix={} path={}",
                node.getFullPrefix(),
                path);

        ensurePathVisibleAndSelected(
                node,
                path);
    }

    private void ensurePathVisibleAndSelected(
            S3TreeNode node,
            TreePath path) {

        TreePath parentPath = path.getParentPath();

        if (parentPath != null
                && !folderTree.isExpanded(parentPath)) {

            log.info(
                    "[TREE SELECT NODE] expanding parentPath={}",
                    parentPath);

            folderTree.expandPath(parentPath);
        }

        folderTree.setSelectionPath(path);
        folderTree.scrollPathToVisible(path);

        log.info(
                "[TREE SELECT NODE FINAL] prefix={} selected={} path={}",
                node.getFullPrefix(),
                folderTree.isPathSelected(path),
                path);
    }
    
    private S3TreeNode findNearestExistingNode(
            String prefix) {

        if (prefix == null) {
            return null;
        }

        S3TreeNode root =
                (S3TreeNode)
                        treeModel.getRoot();

        if (root == null) {
            return null;
        }

        return findNearestExistingNodeRecursive(
                root,
                prefix);
    }

    private S3TreeNode findNearestExistingNodeRecursive(
            S3TreeNode node,
            String prefix) {

        if (node == null) {
            return null;
        }

        String nodePrefix =
                node.getFullPrefix();

        if (nodePrefix == null
                || !prefix.startsWith(nodePrefix)) {

            return null;
        }

        S3TreeNode best =
                node;

        for (int i = 0;
             i < node.getChildCount();
             i++) {

            Object childObject =
                    node.getChildAt(i);

            if (!(childObject instanceof S3TreeNode child)) {
                continue;
            }

            /*
             * Loading marker gerçek bir klasör değildir.
             */
            if (child.isLoading()) {
                continue;
            }

            S3TreeNode candidate =
                    findNearestExistingNodeRecursive(
                            child,
                            prefix);

            if (candidate != null
                    && candidate.getFullPrefix().length()
                    > best.getFullPrefix().length()) {

                best = candidate;
            }
        }

        return best;
    }

    private CompletableFuture<S3TreeNode> ensurePrefixLoaded(
            String prefix) {

        if (prefix == null) {
            return CompletableFuture.completedFuture(null);
        }

        /*
         * Hedef zaten Tree'de varsa doğrudan dön.
         */
        S3TreeNode existing =
                findNodeByPrefix(prefix);

        if (existing != null) {

            log.info(
                    "[TREE NAVIGATION] target already exists prefix={}",
                    prefix);

            return CompletableFuture.completedFuture(
                    existing);
        }

        /*
         * Hedefe ulaşabildiğimiz en yakın mevcut node.
         *
         * Örnek:
         *
         * Tree'de:
         *   SIL3/
         *
         * hedef:
         *   SIL3/DOWNLOAD/2026/
         *
         * parent = SIL3/
         */
        S3TreeNode parent =
                findNearestExistingNode(prefix);

        if (parent == null) {

            log.warn(
                    "[TREE NAVIGATION] parent not found prefix={}",
                    prefix);

            return CompletableFuture.completedFuture(
                    null);
        }

        return ensurePrefixLoadedFromNode(
                parent,
                prefix);
    }

    private CompletableFuture<S3TreeNode> ensurePrefixLoadedFromNode(
            S3TreeNode parent,
            String targetPrefix) {

        if (parent == null
                || targetPrefix == null) {

            return CompletableFuture.completedFuture(
                    null);
        }

        /*
         * Hedef artık mevcut olabilir.
         */
        S3TreeNode existing =
                findNodeByPrefix(targetPrefix);

        if (existing != null) {

            return CompletableFuture.completedFuture(
                    existing);
        }

        String parentPrefix =
                parent.getFullPrefix();

        log.info(
                "[TREE NAVIGATION LOAD] parent={} target={}",
                parentPrefix,
                targetPrefix);

        /*
         * Parent'ın doğrudan çocuklarını yükle.
         *
         * loadChildrenAsync() zaten:
         * - loading marker
         * - async S3 çağrısı
         * - Tree model reload
         * - selection restore
         * işlemlerini yapıyor.
         */
        return loadChildrenAsync(
                parent,
                false)

                .thenCompose(ignored -> {

                    /*
                     * Yükleme sonrasında hedef doğrudan
                     * oluşmuş olabilir.
                     */
                    S3TreeNode target =
                            findNodeByPrefix(targetPrefix);

                    if (target != null) {

                        return CompletableFuture.completedFuture(
                                target);
                    }

                    /*
                     * Parent'ın çocukları arasında hedefe
                     * giden bir sonraki node'u bul.
                     */
                    S3TreeNode next =
                            findNextNodeTowardsPrefix(
                                    parent,
                                    targetPrefix);

                    if (next == null) {

                        log.warn(
                                "[TREE NAVIGATION] next node not found parent={} target={}",
                                parentPrefix,
                                targetPrefix);

                        return CompletableFuture.completedFuture(
                                null);
                    }

                    /*
                     * Bir sonraki seviyeye ilerle.
                     */
                    return ensurePrefixLoadedFromNode(
                            next,
                            targetPrefix);
                });
    }
    
    private S3TreeNode findNextNodeTowardsPrefix(
            S3TreeNode parent,
            String targetPrefix) {

        if (parent == null
                || targetPrefix == null) {

            return null;
        }

        S3TreeNode best =
                null;

        for (int i = 0;
             i < parent.getChildCount();
             i++) {

            Object childObject =
                    parent.getChildAt(i);

            if (!(childObject instanceof S3TreeNode child)) {
                continue;
            }

            /*
             * Loading marker gerçek node değildir.
             */
            if (child.isLoading()) {
                continue;
            }

            String childPrefix =
                    child.getFullPrefix();

            if (childPrefix == null) {
                continue;
            }

            /*
             * Child hedef prefix'in altında olmalı.
             */
            if (!targetPrefix.startsWith(childPrefix)) {
                continue;
            }

            /*
             * Parent'ın doğrudan bir sonraki seviyesi olmalı.
             *
             * En uzun eşleşmeyi seçiyoruz.
             */
            if (best == null
                    || childPrefix.length()
                    > best.getFullPrefix().length()) {

                best = child;
            }
        }

        return best;
    }
    
    public void selectPrefix(String prefix) {

        if (prefix == null) {
            return;
        }

        log.info(
                "[TREE NAVIGATION] selectPrefix target={}",
                prefix);

        S3TreeNode existing =
                findNodeByPrefix(prefix);

        if (existing != null) {

            log.info(
                    "[TREE NAVIGATION] target already exists prefix={}",
                    prefix);

            selectNode(existing);

            return;
        }

        ensurePrefixLoaded(prefix)
                .thenAccept(node -> {

                    if (node == null) {

                        log.warn(
                                "[TREE NAVIGATION] target not found after loading prefix={}",
                                prefix);

                        return;
                    }

                    SwingUtilities.invokeLater(() -> {

                        log.info(
                                "[TREE NAVIGATION] selecting prefix={}",
                                node.getFullPrefix());

                        selectNode(node);
                    });
                })
                .exceptionally(ex -> {

                    log.error(
                            "[TREE NAVIGATION] failed target={}",
                            prefix,
                            ex);

                    return null;
                });
    }
    
    /*
     * ---------------------------------------------------------
     * STATE
     * ---------------------------------------------------------
     */

    public void clearState() {

        treeLoadGenerations.clear();
        nodeCache.clear();

        S3TreeNode root =
                (S3TreeNode)
                        treeModel.getRoot();

        if (root != null) {
            nodeCache.put(
                    root.getFullPrefix(),
                    root);
        }
    }
}