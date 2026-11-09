
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.util.S3Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ExplorerTransferTargetRefreshController {

    private final ExplorerRefreshScheduler refreshScheduler;

    public ExplorerTransferTargetRefreshController(
            ExplorerRefreshScheduler refreshScheduler) {

        this.refreshScheduler =
                Objects.requireNonNull(refreshScheduler);
    }

    public void refreshGroupTargetViews(
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
                S3Util.getParentPrefix(targetPrefix))) {

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
}
