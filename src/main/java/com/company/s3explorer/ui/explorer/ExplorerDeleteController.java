package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ExplorerDeleteController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ExplorerDeleteController.class);

    private final ExplorerView view;
    private final TransferManager transferManager;
    private final ExplorerFileOperationController fileOperationController;

    private final Supplier<String>
            currentBucketSupplier;

    private final Consumer<Boolean>
            restoreFocusConsumer;

    private final Consumer<Integer>
            pendingSelectionRowConsumer;

    private final Set<String>
            pendingFolderDeleteKeys;

    private final Runnable
            updateActionStates;

    public ExplorerDeleteController(
            ExplorerView view,
            TransferManager transferManager,
            ExplorerFileOperationController fileOperationController,
            Supplier<String> currentBucketSupplier,
            Consumer<Boolean> restoreFocusConsumer,
            Consumer<Integer> pendingSelectionRowConsumer,
            Set<String> pendingFolderDeleteKeys,
            Runnable updateActionStates) {

        this.view =
                view;

        this.transferManager =
                transferManager;

        this.fileOperationController =
                fileOperationController;

        this.currentBucketSupplier =
                currentBucketSupplier;

        this.restoreFocusConsumer =
                restoreFocusConsumer;

        this.pendingSelectionRowConsumer =
                pendingSelectionRowConsumer;

        this.pendingFolderDeleteKeys =
                pendingFolderDeleteKeys;

        this.updateActionStates =
                updateActionStates;
    }

    public void deleteSelectedWithFocusRestore() {

        JTable table =
                view.getFileTable();

        boolean restoreFocus =
                table.getSelectedRowCount() > 0;

        restoreFocusConsumer.accept(
                restoreFocus);

        pendingSelectionRowConsumer.accept(
                table.getSelectedRow());

        pendingFolderDeleteKeys.clear();

        for (int viewRow :
                table.getSelectedRows()) {

            int modelRow =
                    table.convertRowIndexToModel(
                            viewRow);

            S3FileItem item =
                    view.getFileTableModel()
                            .getItem(modelRow);

            if (item != null
                    && item.isFolder()) {

                pendingFolderDeleteKeys.add(
                        item.getKey());
            }
        }

        log.info(
                "[DELETE] trigger selectedRows={} " +
                        "restoreFocus={} selectedViewRow={}",
                table.getSelectedRowCount(),
                restoreFocus,
                table.getSelectedRow());

        deleteSelected();
    }

    public void deleteSelected() {

        log.info(
                "[DELETE] invoked selectedRows={} " +
                        "tableFocus={}",
                view.getFileTable()
                        .getSelectedRowCount(),
                view.getFileTable()
                        .hasFocus());

        List<S3FileItem> items =
                getSelectedItems();

        if (items.isEmpty()) {
            return;
        }

        StringBuilder sb =
                new StringBuilder();

        for (S3FileItem item :
                items) {

            if (!sb.isEmpty()) {
                sb.append("\n");
            }

            sb.append(
                    item.getKey());
        }

        String message;

        if (items.size() == 1) {

            message =
                    "Delete "
                            + sb
                            + " ?";

        } else {

            message =
                    "Delete followings?\n"
                            + sb;
        }

        int result =
                JOptionPane.showConfirmDialog(
                        view.getFileTable(),
                        message,
                        "Confirm",
                        JOptionPane.YES_NO_OPTION);

        if (result !=
                JOptionPane.YES_OPTION) {

            return;
        }

        /*
         * ---------------------------------------------------------
         * DELETE CONTEXT
         * ---------------------------------------------------------
         */
        S3FileItem firstItem =
                items.getFirst();

        String repositoryName =
                firstItem.getRepositoryId();

        String bucket =
                currentBucketSupplier.get();

        if (repositoryName == null
                || bucket == null) {

            log.warn(
                    "[DELETE GROUP] missing context " +
                            "repository={} bucket={}",
                    repositoryName,
                    bucket);

            return;
        }

        /*
         * ---------------------------------------------------------
         * SINGLE DELETE
         * ---------------------------------------------------------
         */
        if (items.size() == 1) {

            S3FileItem item =
                    items.getFirst();

            try {

                fileOperationController.delete(
                        item);

                log.info(
                        "[DELETE] submitted source={} group=NONE",
                        item.getKey());

            } catch (Exception ex) {

                log.error(
                        "[DELETE] failed source={}",
                        item.getKey(),
                        ex);

                showError(
                        ex,
                        "Delete Failed");

                return;
            }

            updateActionStates.run();

            return;
        }

        /*
         * ---------------------------------------------------------
         * MULTI DELETE GROUP
         * ---------------------------------------------------------
         */
        String sourcePrefix;

        if (firstItem.isFolder()) {

            sourcePrefix =
                    firstItem.getKey();

        } else {

            sourcePrefix =
                    S3Util.extractParentPrefix(
                            firstItem.getKey());
        }

        String groupName =
                getOperationGroupName(
                        items);

        TransferGroup group =
                transferManager.createOperationGroup(
                        TransferType.DELETE_GROUP,
                        groupName,
                        repositoryName,
                        bucket,
                        sourcePrefix,
                        null,
                        bucket,
                        sourcePrefix);

        transferManager.configureGroupCompletion(
                group,
                repositoryName,
                bucket,
                sourcePrefix,
                true);

        log.info(
                "[DELETE GROUP] created group={} " +
                        "sourcePrefix={} itemCount={}",
                group.getDisplayName(),
                sourcePrefix,
                items.size());

        /*
         * ---------------------------------------------------------
         * SUBMIT DELETE TASKS
         * ---------------------------------------------------------
         */
        for (S3FileItem item :
                items) {

            try {

                if (item.isFolder()) {

                    transferManager.submitFolderDelete(
                            repositoryName,
                            bucket,
                            item.getKey(),
                            group);

                    log.info(
                            "[DELETE GROUP] submitted folder " +
                                    "source={} group={}",
                            item.getKey(),
                            group.getDisplayName());

                } else {

                    fileOperationController.delete(
                            item,
                            group);

                    log.info(
                            "[DELETE GROUP] submitted file " +
                                    "source={} group={}",
                            item.getKey(),
                            group.getDisplayName());
                }

            } catch (Exception ex) {

                log.error(
                        "[DELETE GROUP] failed source={} " +
                                "group={}",
                        item.getKey(),
                        group.getDisplayName(),
                        ex);

                showError(
                        ex,
                        "Delete Failed");

                group.failed();
            }
        }

        group.markProductionCompleted();

        updateActionStates.run();
    }

    private List<S3FileItem> getSelectedItems() {

        int[] viewRows =
                view.getFileTable()
                        .getSelectedRows();

        java.util.ArrayList<S3FileItem> items =
                new java.util.ArrayList<>();

        for (int viewRow :
                viewRows) {

            int modelRow =
                    view.getFileTable()
                            .convertRowIndexToModel(
                                    viewRow);

            S3FileItem item =
                    view.getFileTableModel()
                            .getItem(modelRow);

            if (item != null
                    && !item.isParentFolder()) {

                items.add(item);
            }
        }

        return items;
    }

    private String getOperationGroupName(
            List<S3FileItem> items) {

        if (items == null
                || items.isEmpty()) {

            return "";
        }

        String firstName =
                items.getFirst()
                        .getName();

        if (items.size() == 1) {
            return firstName;
        }

        return firstName
                + " and others";
    }

    private void showError(
            Exception ex,
            String title) {

        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        view.getFileTable(),
                        ex.getMessage(),
                        title,
                        JOptionPane.ERROR_MESSAGE));
    }
}