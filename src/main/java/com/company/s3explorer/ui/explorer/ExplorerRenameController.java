package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ExplorerRenameController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ExplorerRenameController.class);

    private final ExplorerView view;
    private final TransferManager transferManager;

    private final Predicate<String>
            existsPredicate;

    private final Runnable
            updateActionStates;

    private final Consumer<String>
            pendingRenameOldKeyConsumer;

    private final Consumer<String>
            pendingSelectionKeyConsumer;

    private final Consumer<Boolean>
            restoreFocusConsumer;

    public ExplorerRenameController(
            ExplorerView view,
            TransferManager transferManager,
            Supplier<String> currentBucketSupplier,
            Predicate<String> existsPredicate,
            Runnable updateActionStates,
            Consumer<String> pendingRenameOldKeyConsumer,
            Consumer<String> pendingSelectionKeyConsumer,
            Consumer<Boolean> restoreFocusConsumer) {

        this.view =
                view;

        this.transferManager =
                transferManager;

        this.existsPredicate =
                existsPredicate;

        this.updateActionStates =
                updateActionStates;

        this.pendingRenameOldKeyConsumer =
                pendingRenameOldKeyConsumer;

        this.pendingSelectionKeyConsumer =
                pendingSelectionKeyConsumer;

        this.restoreFocusConsumer =
                restoreFocusConsumer;
    }

    public void renameSelected() {

        JTable table =
                view.getFileTable();

        int selectedRowCount =
                table.getSelectedRowCount();

        if (selectedRowCount != 1) {

            log.warn(
                    "[RENAME] exactly one item must be selected selectedRows={}",
                    selectedRowCount);

            return;
        }

        int viewRow =
                table.getSelectedRow();

        if (viewRow < 0) {
            return;
        }

        int modelRow =
                table.convertRowIndexToModel(
                        viewRow);

        S3FileItem item =
                view.getFileTableModel()
                        .getItem(modelRow);

        if (item == null
                || item.isParentFolder()) {

            log.warn(
                    "[RENAME] invalid selected item");

            return;
        }

        String oldKey =
                item.getKey();

        String oldName =
                item.getName();

        String newName =
                JOptionPane.showInputDialog(
                        view.getFileTable(),
                        "New name:",
                        oldName);

        if (newName == null) {
            return;
        }

        newName =
                newName.trim();

        if (newName.isBlank()) {

            JOptionPane.showMessageDialog(
                    view.getFileTable(),
                    "New name can not be empty.",
                    "Rename",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        if (newName.equals(oldName)) {
            return;
        }

        /*
         * A rename changes only the item name.
         * Path separators are therefore not allowed.
         */
        if (newName.contains("/")
                || newName.contains("\\")) {

            JOptionPane.showMessageDialog(
                    view.getFileTable(),
                    "New name can not contain folder.",
                    "Rename",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        String parentPrefix =
                S3Util.extractParentPrefix(
                        oldKey);

        String newKey =
                S3Util.combineKey(
                        parentPrefix,
                        newName);

        if (item.isFolder()) {
            newKey += "/";
        }

        if (existsPredicate.test(newKey)) {

            log.warn(
                    "[RENAME] target already exists source={} target={}",
                    oldKey,
                    newKey);

            JOptionPane.showMessageDialog(
                    view.getFileTable(),
                    newName + " is already used.\n"
                            + "Please select a different one.",
                    "Rename",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        String repositoryName =
                item.getRepositoryId();

        String bucket =
                item.getBucket();

        if (repositoryName == null
                || bucket == null) {

            log.warn(
                    "[RENAME] repository/bucket missing oldKey={}",
                    oldKey);

            return;
        }

        /*
         * The refresh following the rename must select
         * the newly created item.
         */
        pendingRenameOldKeyConsumer.accept(
                oldKey);

        pendingSelectionKeyConsumer.accept(
                newKey);

        restoreFocusConsumer.accept(
                true);

        log.info(
                "[RENAME] source={} target={} folder={}",
                oldKey,
                newKey,
                item.isFolder());

        try {

            if (item.isFolder()) {

                transferManager.submitFolderRename(
                        repositoryName,
                        bucket,
                        oldKey,
                        newKey);

            } else {

                transferManager.submitRename(
                        repositoryName,
                        bucket,
                        oldKey,
                        repositoryName,
                        bucket,
                        newKey,
                        item.getSize(),
                        false);
            }

        } catch (Exception ex) {

            pendingRenameOldKeyConsumer.accept(
                    null);

            pendingSelectionKeyConsumer.accept(
                    null);

            restoreFocusConsumer.accept(
                    false);

            log.error(
                    "[RENAME] failed source={} target={}",
                    oldKey,
                    newKey,
                    ex);

            JOptionPane.showMessageDialog(
                    view.getFileTable(),
                    ex.getMessage(),
                    "Rename Failed",
                    JOptionPane.ERROR_MESSAGE);

            return;
        }

        updateActionStates.run();
    }
}