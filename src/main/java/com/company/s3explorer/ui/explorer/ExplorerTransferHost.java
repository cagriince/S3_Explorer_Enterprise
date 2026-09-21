package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.model.TransferTask;

import java.util.List;
import java.util.UUID;

public interface ExplorerTransferHost {

    List<TransferTask> getCompletedGroupTasks(UUID groupId);

    void clearCompletedGroupTasks(UUID groupId);

    String getCurrentFileBucket();

    String getCurrentFilePrefix();

    String getParentPrefix(String key);

    ExplorerView getExplorerView();

    void scheduleTreeRefresh(
            List<RefreshTreeNode> refreshes);

    void restoreFileTableFocus();

    void restoreFileTableSelectionAfterDelete();

    boolean restorePendingPasteSelection();

    void restoreFileTableSelectionByKey(
            String key);

    int getPendingDeleteSelectionViewRow();

    void clearPendingDeleteSelectionViewRow();

    List<String> getPendingFileTableSelectionKeys();

    String getPendingFileTableSelectionKey();

    boolean isRestoreFileTableFocus();

    ExplorerTreeController getTreeController();

    void updateActionStates();
}