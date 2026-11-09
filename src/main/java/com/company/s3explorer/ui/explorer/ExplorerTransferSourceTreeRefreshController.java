
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.model.TransferGroup;

import java.util.List;
import java.util.Objects;

public class ExplorerTransferSourceTreeRefreshController {

    private final ExplorerRefreshScheduler refreshScheduler;

    public ExplorerTransferSourceTreeRefreshController(
            ExplorerRefreshScheduler refreshScheduler) {

        this.refreshScheduler =
                Objects.requireNonNull(refreshScheduler);
    }

    public void refreshSourceTree(
            TransferGroup group,
            TransferGroupCompletedEvent event,
            String currentBucket) {

        if (group == null
                || event == null
                || !event.isSourceRefreshRequired()) {
            return;
        }

        TransferType operation = group.getOperation();

        /*
         * DELETE_GROUP / MOVE_GROUP
         *
         * Kaynak klasörün kendisini Tree'den kaldır.
         */
        if (operation == TransferType.DELETE_GROUP
                || operation == TransferType.MOVE_GROUP) {

            String sourceBucket =
                    group.getSourceBucket();

            String sourcePrefix =
                    group.getSourcePrefix();

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

                refreshScheduler.scheduleRefresh(
                        List.of(
                                new RefreshTreeNode(
                                        folderPrefix,
                                        RefreshTreeOperation.DELETE)));
            }

            return;
        }

        /*
         * Normal folder source operation.
         */
        if (group.isSourceFolder()) {

            String sourceBucket =
                    event.getBucket();

            String sourcePrefix =
                    event.getPrefix();

            if (Objects.equals(currentBucket, sourceBucket)) {

                refreshScheduler.scheduleRefresh(
                        List.of(
                                new RefreshTreeNode(
                                        sourcePrefix,
                                        RefreshTreeOperation.DELETE)));
            }
        }
    }
}
