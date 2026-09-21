package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Handles completion of a transfer group.
     *
     * At this stage of the refactor this controller is
     * responsible only for incremental file-table updates
     * caused by COPY_GROUP, MOVE_GROUP and DELETE_GROUP.
     *
     * Rename, tree refresh, source refresh, target refresh
     * and selection restoration remain in ExplorerPanel
     * until their corresponding state is moved to the host.
     */
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
            completedTasks = List.of();
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
                        "targetBucket={} " +
                        "targetPrefix={} " +
                        "sourceIsFolder={}",
                currentBucket,
                currentPrefix,
                group.getTargetBucket(),
                group.getTargetPrefix(),
                group.isSourceFolder());

        handleIncrementalGroupOperations(
                group,
                completedTasks,
                currentBucket,
                currentPrefix);
    }

    /**
     * Applies incremental changes to the current file table
     * for group COPY, MOVE and DELETE operations.
     */
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

            if (task.getType() == TransferType.DELETE) {

                handleGroupDeleteTask(
                        group,
                        task,
                        currentBucket,
                        currentPrefix);

                continue;
            }

            if (task.getType() == TransferType.COPY
                    || task.getType() == TransferType.MOVE) {

                handleGroupCopyMoveTask(
                        group,
                        task,
                        currentBucket,
                        currentPrefix);
            }
        }
    }

    /**
     * Removes a successfully deleted object from
     * the currently displayed file table.
     */
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

    /**
     * Applies the target-side insertion for COPY/MOVE and,
     * for MOVE, removes the source object from the current
     * file table when the source is currently displayed.
     */
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
         * Target side.
         *
         * If the target object belongs to the currently
         * displayed folder, insert it into the file table.
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
         * Source side.
         *
         * MOVE removes the source object from the currently
         * displayed folder after the transfer succeeds.
         */
        if (task.getType() == TransferType.MOVE
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
}