package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExplorerTransferController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ExplorerTransferController.class);

    private final ExplorerTransferHost host;

    public ExplorerTransferController(
            ExplorerTransferHost host) {

        this.host = host;
    }

    public void onTransferGroupCompleted(
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
                host.getCompletedGroupTasks(
                        group.getId());

        if (completedTasks == null) {
            completedTasks =
                    List.of();
        }

        host.clearCompletedGroupTasks(
                group.getId());

        log.info(
                "[EXPLORER GROUP TASKS] " +
                        "group={} collectedTasks={} detected={}",
                group.getDisplayName(),
                completedTasks.size(),
                group.getDetected());

        String currentBucket =
                host.getCurrentFileBucket();

        String currentPrefix =
                host.getCurrentFilePrefix();

        log.info(
                "[EXPLORER GROUP REFRESH STATE] " +
                        "currentFileBucket={} " +
                        "currentFilePrefix={} " +
                        "currentBucket={} " +
                        "currentPrefix={} " +
                        "targetBucket={} " +
                        "targetPrefix={} " +
                        "sourceIsFolder={}",
                currentBucket,
                currentPrefix,
                currentBucket,
                currentPrefix,
                group.getTargetBucket(),
                group.getTargetPrefix(),
                group.isSourceFolder());

        /*
         * =========================================================
         * INCREMENTAL GROUP OPERATIONS
         * =========================================================
         */
        handleIncrementalGroupOperations(
                group,
                completedTasks,
                currentBucket,
                currentPrefix);

        /*
         * =========================================================
         * RENAME
         * =========================================================
         */
        if (group.getOperation()
                == TransferType.RENAME
                || group.getOperation()
                == TransferType.RENAME_GROUP) {

            handleRename(
                    event,
                    group,
                    currentBucket,
                    currentPrefix);

            return;
        }

        /*
         * =========================================================
         * DELETE GROUP - TREE
         * =========================================================
         */
        handleDeleteGroupTreeRefresh(
                event,
                group,
                completedTasks,
                currentBucket);

        /*
         * =========================================================
         * SOURCE REFRESH
         * =========================================================
         */
        handleSourceRefresh(
                event,
                group,
                completedTasks,
                currentBucket,
                currentPrefix);

        /*
         * =========================================================
         * TARGET REFRESH
         * =========================================================
         */
        handleTargetRefresh(
                group,
                completedTasks,
                currentBucket);
    }

    private void handleIncrementalGroupOperations(
            TransferGroup group,
            List<TransferTask> completedTasks,
            String currentBucket,
            String currentPrefix) {

        if (completedTasks.isEmpty()) {
            return;
        }

        TransferType operation =
                group.getOperation();

        if (operation != TransferType.COPY_GROUP
                && operation != TransferType.MOVE_GROUP
                && operation != TransferType.DELETE_GROUP) {

            return;
        }

        for (TransferTask task : completedTasks) {

            if (task == null) {
                continue;
            }

            if (task.getType()
                    == TransferType.DELETE) {

                handleGroupDeleteTask(
                        group,
                        task,
                        currentBucket,
                        currentPrefix);

                continue;
            }

            if (task.getType()
                    == TransferType.COPY
                    || task.getType()
                    == TransferType.MOVE) {

                handleGroupCopyMoveTask(
                        group,
                        task,
                        currentBucket,
                        currentPrefix);
            }
        }

        /*
         * DELETE group selection restore
         * burada yalnızca DELETE_GROUP için çalışır.
         */
        handleDeleteGroupSelectionRestore(
                group);
    }

    private void handleGroupDeleteTask(
            TransferGroup group,
            TransferTask task,
            String currentBucket,
            String currentPrefix) {

        if (!Objects.equals(
                currentBucket,
                task.getBucket())) {

            return;
        }

        if (!Objects.equals(
                currentPrefix,
                host.getParentPrefix(
                        task.getObjectKey()))) {

            return;
        }

        boolean removed =
                host.getExplorerView()
                        .getFileTableModel()
                        .removeFileByKey(
                                task.getObjectKey());

        log.info(
                "[FILE TABLE GROUP DELETE REMOVE] " +
                        "key={} removed={} group={}",
                task.getObjectKey(),
                removed,
                group.getDisplayName());
    }

    private void handleGroupCopyMoveTask(
            TransferGroup group,
            TransferTask task,
            String currentBucket,
            String currentPrefix) {

        String targetBucket =
                task.getTargetBucket();

        String targetKey =
                task.getTargetObjectKey();

        if (targetBucket == null
                || targetKey == null
                || targetKey.isBlank()) {

            return;
        }

        String targetParentPrefix =
                host.getParentPrefix(
                        targetKey);

        /*
         * ---------------------------------------------------------
         * TARGET FILE TABLE
         * ---------------------------------------------------------
         */
        if (Objects.equals(
                currentBucket,
                targetBucket)
                && Objects.equals(
                currentPrefix,
                targetParentPrefix)) {

            boolean targetIsFolder =
                    targetKey.endsWith("/");

            S3FileItem item =
                    new S3FileItem(
                            task.getTargetRepositoryName(),
                            targetBucket,
                            targetKey,
                            task.getSize(),
                            null,
                            null,
                            targetIsFolder);

            boolean added =
                    host.getExplorerView()
                            .getFileTableModel()
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
         * ---------------------------------------------------------
         * MOVE - SOURCE FILE TABLE
         * ---------------------------------------------------------
         */
        if (task.getType()
                == TransferType.MOVE
                && Objects.equals(
                currentBucket,
                task.getBucket())
                && Objects.equals(
                currentPrefix,
                host.getParentPrefix(
                        task.getObjectKey()))) {

            boolean removed =
                    host.getExplorerView()
                            .getFileTableModel()
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

    /*
     * =============================================================
     * DELETE GROUP SELECTION RESTORE
     * =============================================================
     */
    private void handleDeleteGroupSelectionRestore(
            TransferGroup group) {

        if (group.getOperation()
                != TransferType.DELETE_GROUP) {

            return;
        }

        if (host.getPendingDeleteSelectionViewRow()
                < 0) {

            return;
        }

        SwingUtilities.invokeLater(() -> {

            host.restoreFileTableSelectionAfterDelete();

            host.clearPendingDeleteSelectionViewRow();

            host.restoreFileTableFocus();

            host.updateActionStates();

            log.info(
                    "[DELETE GROUP SELECTION RESTORE] completed");
        });
    }

    /*
     * =============================================================
     * RENAME
     * =============================================================
     */
    private void handleRename(
            TransferGroupCompletedEvent event,
            TransferGroup group,
            String currentBucket,
            String currentPrefix) {

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
         * ---------------------------------------------------------
         * FILE TABLE - SOURCE
         * ---------------------------------------------------------
         */
        if (Objects.equals(
                currentBucket,
                sourceBucket)) {

            String sourceParentPrefix =
                    host.getParentPrefix(
                            sourceKey);

            if (Objects.equals(
                    currentPrefix,
                    sourceParentPrefix)) {

                if (sourceIsFolder) {

                    boolean removed =
                            host.getExplorerView()
                                    .getFileTableModel()
                                    .removeFileByKey(
                                            sourceKey);

                    log.info(
                            "[FILE TABLE RENAME REMOVE] " +
                                    "folder key={} removed={}",
                            sourceKey,
                            removed);

                } else {

                    int sourceRow =
                            host.getExplorerView()
                                    .getFileTableModel()
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
                                host.getExplorerView()
                                        .getFileTableModel()
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
                                            sourceItem.getRepositoryName(),
                                            targetBucket,
                                            targetKey,
                                            sourceItem.getSize(),
                                            sourceItem.getLastModified(),
                                            sourceItem.getStorageClass(),
                                            false);

                            boolean replaced =
                                    host.getExplorerView()
                                            .getFileTableModel()
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
         * ---------------------------------------------------------
         * FILE TABLE - TARGET FOLDER
         * ---------------------------------------------------------
         */
        if (sourceIsFolder
                && Objects.equals(
                currentBucket,
                targetBucket)) {

            String targetParentPrefix =
                    host.getParentPrefix(
                            targetKey);

            if (Objects.equals(
                    currentPrefix,
                    targetParentPrefix)) {

                boolean added =
                        host.getExplorerView()
                                .getFileTableModel()
                                .addFile(
                                        new S3FileItem(
                                                group.getTargetRepository(),
                                                targetBucket,
                                                targetKey,
                                                0L,
                                                null,
                                                null,
                                                true));

                log.info(
                        "[FILE TABLE RENAME INSERT] " +
                                "folder key={} added={}",
                        targetKey,
                        added);
            }
        }

        /*
         * ---------------------------------------------------------
         * FOLDER TREE
         * ---------------------------------------------------------
         */
        if (sourceIsFolder) {

            boolean renamed =
                    false;

            if (Objects.equals(
                    currentBucket,
                    sourceBucket)) {

                ExplorerTreeController treeController =
                        host.getTreeController();

                if (treeController != null) {

                    renamed =
                            treeController
                                    .renameNodePreservingChildren(
                                            sourceKey,
                                            targetKey);
                }
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
         * ---------------------------------------------------------
         * RENAME SELECTION
         * ---------------------------------------------------------
         */
        String pendingSelectionKey =
                host.getPendingFileTableSelectionKey();

        if (pendingSelectionKey != null) {

            SwingUtilities.invokeLater(() -> {

                host.restoreFileTableSelectionByKey(
                        pendingSelectionKey);

                if (host.isRestoreFileTableFocus()) {

                    host.restoreFileTableFocus();
                }
            });
        }
    }

    /*
     * =============================================================
     * DELETE GROUP TREE REFRESH
     * =============================================================
     */
    private void handleDeleteGroupTreeRefresh(
            TransferGroupCompletedEvent event,
            TransferGroup group,
            List<TransferTask> completedTasks,
            String currentBucket) {

        if (group.getOperation()
                != TransferType.DELETE_GROUP) {

            return;
        }

        if (!event.isSourceRefreshRequired()) {

            return;
        }

        List<RefreshTreeNode> deleteRefreshes =
                new ArrayList<>();

        for (TransferTask task :
                completedTasks) {

            if (task == null
                    || task.getType()
                    != TransferType.DELETE) {

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

            host.scheduleTreeRefresh(
                    deleteRefreshes);
        }
    }

    /*
     * =============================================================
     * SOURCE REFRESH
     * =============================================================
     */
    private void handleSourceRefresh(
            TransferGroupCompletedEvent event,
            TransferGroup group,
            List<TransferTask> completedTasks,
            String currentBucket,
            String currentPrefix) {

        /*
         * DELETE_GROUP kendi özel mekanizmasıyla işleniyor.
         */
        if (!event.isSourceRefreshRequired()
                || group.getOperation()
                == TransferType.DELETE_GROUP) {

            return;
        }

        /*
         * ---------------------------------------------------------
         * MOVE GROUP
         * ---------------------------------------------------------
         */
        if (group.getOperation()
                == TransferType.MOVE_GROUP) {

            List<RefreshTreeNode> sourceTreeRefreshes =
                    new ArrayList<>();

            for (TransferTask task :
                    completedTasks) {

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
                        "[EXPLORER SOURCE FOLDER TREE REFRESH] " +
                                "group={} operation={} prefixes={}",
                        group.getDisplayName(),
                        group.getOperation(),
                        sourceTreeRefreshes);

                host.scheduleTreeRefresh(
                        sourceTreeRefreshes);
            }

            /*
             * File Table source remove
             */
            for (TransferTask task :
                    completedTasks) {

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
                        host.getParentPrefix(
                                sourceKey))) {

                    continue;
                }

                boolean removed =
                        host.getExplorerView()
                                .getFileTableModel()
                                .removeFileByKey(
                                        sourceKey);

                log.info(
                        "[FILE TABLE GROUP MOVE REMOVE] " +
                                "source={} removed={} group={}",
                        sourceKey,
                        removed,
                        group.getDisplayName());
            }

            return;
        }

        /*
         * ---------------------------------------------------------
         * NORMAL / LEGACY SOURCE REFRESH
         * ---------------------------------------------------------
         */
        String sourceBucket =
                event.getBucket();

        String sourcePrefix =
                event.getPrefix();

        if (group.isSourceFolder()) {

            String sourceParentPrefix =
                    host.getParentPrefix(
                            sourcePrefix);

            if (Objects.equals(
                    currentBucket,
                    sourceBucket)) {

                log.debug(
                        "[EXPLORER SOURCE TREE REFRESH] " +
                                "prefix={} operation=DELETE",
                        sourcePrefix);

                host.scheduleTreeRefresh(
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
                        host.getExplorerView()
                                .getFileTableModel()
                                .removeFileByKey(
                                        sourcePrefix);

                log.info(
                        "[FILE TABLE ROW REMOVE] " +
                                "key={} removed={}",
                        sourcePrefix,
                        removed);

                if (removed
                        && host.getPendingDeleteSelectionViewRow()
                        >= 0) {

                    SwingUtilities.invokeLater(() -> {

                        host.restoreFileTableSelectionAfterDelete();

                        host.clearPendingDeleteSelectionViewRow();

                        host.restoreFileTableFocus();
                    });
                }
            }

        } else {

            if (Objects.equals(
                    currentBucket,
                    sourceBucket)
                    && Objects.equals(
                    currentPrefix,
                    host.getParentPrefix(
                            sourcePrefix))) {

                boolean removed =
                        host.getExplorerView()
                                .getFileTableModel()
                                .removeFileByKey(
                                        sourcePrefix);

                log.info(
                        "[FILE TABLE ROW REMOVE] " +
                                "key={} removed={}",
                        sourcePrefix,
                        removed);
            }
        }
    }

    /*
     * =============================================================
     * TARGET REFRESH
     * =============================================================
     */
    private void handleTargetRefresh(
            TransferGroup group,
            List<TransferTask> completedTasks,
            String currentBucket) {

        TransferType operation =
                group.getOperation();

        if (operation != TransferType.COPY
                && operation != TransferType.MOVE
                && operation != TransferType.COPY_GROUP
                && operation != TransferType.MOVE_GROUP
                && operation != TransferType.UPLOAD_GROUP) {

            return;
        }

        String targetBucket =
                group.getTargetBucket();

        if (targetBucket == null
                || !Objects.equals(
                currentBucket,
                targetBucket)) {

            return;
        }

        List<RefreshTreeNode> targetTreeRefreshes =
                new ArrayList<>();

        /*
         * ---------------------------------------------------------
         * COPY_GROUP / MOVE_GROUP
         * ---------------------------------------------------------
         *
         * Gerçek oluşturulan folder key'lerini kullan.
         */
        if (operation
                == TransferType.COPY_GROUP
                || operation
                == TransferType.MOVE_GROUP) {

            for (TransferTask task :
                    completedTasks) {

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
                        operation
                                == TransferType.UPLOAD_GROUP
                                ? host.getParentPrefix(
                                targetPrefix)
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
                    operation,
                    targetTreeRefreshes);

            host.scheduleTreeRefresh(
                    targetTreeRefreshes);
        }

        /*
         * ---------------------------------------------------------
         * PASTE SELECTION RESTORE
         * ---------------------------------------------------------
         */
        if (host.getPendingFileTableSelectionKeys() != null
                && !host.getPendingFileTableSelectionKeys()
                .isEmpty()) {

            SwingUtilities.invokeLater(() -> {

                boolean restored =
                        host.restorePendingPasteSelection();

                if (restored) {

                    host.restoreFileTableFocus();

                    log.info(
                            "[FILE TABLE GROUP SELECTION] " +
                                    "restored keys={}",
                            host.getPendingFileTableSelectionKeys());
                }
            });
        }
    }
}