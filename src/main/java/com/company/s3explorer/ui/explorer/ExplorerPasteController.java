package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ExplorerPasteController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ExplorerPasteController.class);

    private final ExplorerClipboard clipboard;
    private final TransferManager transferManager;
    private final ExplorerFileOperationController fileOperationController;

    private final Supplier<RepositoryDefinition>
            currentRepositorySupplier;

    private final Supplier<String>
            currentBucketSupplier;

    private final Supplier<String>
            currentPrefixSupplier;

    private final Predicate<String>
            existsPredicate;

    private final BiPredicate<S3FileItem, String>
            conflictConfirmation;

    private final Consumer<Boolean>
            pasteSelectionProgressConsumer;

    private final Consumer<List<String>>
            pendingSelectionConsumer;

    private final Consumer<Boolean>
            restoreFocusConsumer;

    private final Consumer<Boolean>
            forceFocusConsumer;

    private final Runnable
            updateActionStates;

    public ExplorerPasteController(
            ExplorerClipboard clipboard,
            TransferManager transferManager,
            ExplorerFileOperationController fileOperationController,
            Supplier<RepositoryDefinition> currentRepositorySupplier,
            Supplier<String> currentBucketSupplier,
            Supplier<String> currentPrefixSupplier,
            Predicate<String> existsPredicate,
            BiPredicate<S3FileItem, String> conflictConfirmation,
            Consumer<Boolean> pasteSelectionProgressConsumer,
            Consumer<List<String>> pendingSelectionConsumer,
            Consumer<Boolean> restoreFocusConsumer,
            Consumer<Boolean> forceFocusConsumer,
            Runnable updateActionStates) {

        this.clipboard =
                clipboard;

        this.transferManager =
                transferManager;

        this.fileOperationController =
                fileOperationController;

        this.currentRepositorySupplier =
                currentRepositorySupplier;

        this.currentBucketSupplier =
                currentBucketSupplier;

        this.currentPrefixSupplier =
                currentPrefixSupplier;

        this.existsPredicate =
                existsPredicate;

        this.conflictConfirmation =
                conflictConfirmation;

        this.pasteSelectionProgressConsumer =
                pasteSelectionProgressConsumer;

        this.pendingSelectionConsumer =
                pendingSelectionConsumer;

        this.restoreFocusConsumer =
                restoreFocusConsumer;

        this.forceFocusConsumer =
                forceFocusConsumer;

        this.updateActionStates =
                updateActionStates;
    }

    public void paste() {

        if (clipboard.isEmpty()) {
            return;
        }

        String targetBucket =
                currentBucketSupplier.get();

        String targetPrefix =
                currentPrefixSupplier.get();

        if (targetBucket == null
                || targetPrefix == null) {

            return;
        }

        List<S3FileItem> items =
                new ArrayList<>(
                        clipboard.getItems());

        ExplorerClipboard.Operation operation =
                clipboard.getOperation();

        executePaste(
                targetBucket,
                targetPrefix,
                items,
                operation);
    }

    private void executePaste(
            String targetBucket,
            String targetPrefix,
            List<S3FileItem> items,
            ExplorerClipboard.Operation operation) {

        if (targetBucket == null
                || targetPrefix == null
                || items == null
                || items.isEmpty()
                || operation == null) {

            return;
        }

        pasteSelectionProgressConsumer.accept(true);

        List<String> selectionKeys =
                new ArrayList<>();

        TransferGroup group =
                null;

        int skippedCount =
                0;

        boolean folderProducerSubmitted =
                false;

        for (S3FileItem item : items) {

            if (item == null) {
                continue;
            }

            String targetSubmissionKey;
            String targetSelectionKey;

            if (item.isFolder()) {

                targetSubmissionKey =
                        targetPrefix;

                targetSelectionKey =
                        S3Util.combineKey(
                                targetPrefix,
                                item.getName());

                if (!targetSelectionKey.endsWith("/")) {
                    targetSelectionKey += "/";
                }

            } else {

                targetSubmissionKey =
                        S3Util.combineKey(
                                targetPrefix,
                                item.getName());

                targetSelectionKey =
                        targetSubmissionKey;
            }

            /*
             * Do not paste an item onto itself.
             */
            if (item.getBucket().equals(targetBucket)
                    && item.getKey().equals(
                    targetSelectionKey)) {

                log.info(
                        "[PASTE] skipped self-target source={} target={}",
                        item.getKey(),
                        targetSelectionKey);

                continue;
            }

            /*
             * Folder Copy/Move silently merges.
             *
             * File Copy/Move requires conflict confirmation.
             */
            boolean overwrite =
                    false;

            if (!item.isFolder()
                    && existsPredicate.test(
                    targetSubmissionKey)) {

                if (!conflictConfirmation.test(
                        item,
                        targetSubmissionKey)) {

                    if (group != null) {
                        group.skipped();
                    } else {
                        skippedCount++;
                    }

                    log.info(
                            operation ==
                                    ExplorerClipboard.Operation.COPY
                                    ? "[COPY] skipped by user source={} target={}"
                                    : "[MOVE] skipped by user source={} target={}",
                            item.getKey(),
                            targetSubmissionKey);

                    log.info(
                            "[PASTE SELECTION] not selected key={} item={}",
                            targetSelectionKey,
                            item.getName());

                    continue;
                }

                overwrite = true;
            }

            /*
             * Create the shared group only after
             * the item has actually been accepted.
             */
            if (group == null) {

                String groupName =
                        getOperationGroupName(items);

                String sourcePrefix;

                if (item.isFolder()) {

                    sourcePrefix =
                            item.getKey();

                } else {

                    sourcePrefix =
                            S3Util.extractParentPrefix(
                                    item.getKey());
                }

                TransferType groupOperation =
                        operation ==
                                ExplorerClipboard.Operation.COPY
                                ? TransferType.COPY_GROUP
                                : TransferType.MOVE_GROUP;

                RepositoryDefinition repository =
                        currentRepositorySupplier.get();

                if (repository == null) {
                    pasteSelectionProgressConsumer.accept(false);
                    return;
                }

                group =
                        transferManager.createOperationGroup(
                                groupOperation,
                                groupName,
                                item.getRepositoryId(),
                                item.getBucket(),
                                sourcePrefix,
                                repository.getId(),
                                targetBucket,
                                targetPrefix);

                transferManager.configureGroupCompletion(
                        group,
                        item.getRepositoryId(),
                        item.getBucket(),
                        sourcePrefix,
                        operation ==
                                ExplorerClipboard.Operation.MOVE);

                log.info(
                        "[PASTE GROUP] created operation={} " +
                                "group={} sourcePrefix={} " +
                                "targetPrefix={} " +
                                "sourceRefreshRequired={}",
                        groupOperation,
                        group.getDisplayName(),
                        sourcePrefix,
                        targetPrefix,
                        operation ==
                                ExplorerClipboard.Operation.MOVE);

                if (skippedCount > 0) {

                    for (int i = 0;
                         i < skippedCount;
                         i++) {

                        group.skipped();
                    }

                    log.info(
                            "[PASTE GROUP] transferred skipped " +
                                    "decisions count={} group={}",
                            skippedCount,
                            group.getDisplayName());

                    skippedCount = 0;
                }
            }

            boolean submitted;

            if (operation ==
                    ExplorerClipboard.Operation.COPY) {

                submitted =
                        submitCopy(
                                item,
                                targetBucket,
                                targetSubmissionKey,
                                overwrite,
                                group);

            } else {

                submitted =
                        submitMove(
                                item,
                                targetBucket,
                                targetSubmissionKey,
                                overwrite,
                                group);
            }

            if (submitted) {

                selectionKeys.add(
                        targetSelectionKey);

                log.info(
                        "[PASTE SELECTION] accepted key={} item={}",
                        targetSelectionKey,
                        item.getName());

                if (item.isFolder()) {
                    folderProducerSubmitted = true;
                }

            } else {

                log.info(
                        "[PASTE SELECTION] not selected key={} item={}",
                        targetSelectionKey,
                        item.getName());
            }
        }

        pasteSelectionProgressConsumer.accept(false);

        /*
         * For folder operations the folder producer owns
         * the production lifecycle.
         *
         * File-only operations complete production here.
         */
        if (group != null
                && !folderProducerSubmitted) {

            group.markProductionCompleted();

            log.info(
                    "[PASTE GROUP] production completed immediately " +
                            "group={} queued={} running={} completed={} " +
                            "failed={} cancelled={}",
                    group.getDisplayName(),
                    group.getQueued(),
                    group.getRunning(),
                    group.getCompleted(),
                    group.getFailed(),
                    group.getCancelled());

        } else if (group != null) {

            log.info(
                    "[PASTE GROUP] production remains owned by folder " +
                            "producer group={} queued={} running={} " +
                            "completed={} failed={} cancelled={}",
                    group.getDisplayName(),
                    group.getQueued(),
                    group.getRunning(),
                    group.getCompleted(),
                    group.getFailed(),
                    group.getCancelled());
        }

        if (!selectionKeys.isEmpty()) {

            pendingSelectionConsumer.accept(
                    new ArrayList<>(selectionKeys));

            restoreFocusConsumer.accept(true);

            forceFocusConsumer.accept(true);

            log.info(
                    "[PASTE SELECTION] final pending keys={} " +
                            "restoreFocus=true forceFocus=true",
                    selectionKeys);

        } else {

            pendingSelectionConsumer.accept(
                    null);

            log.info(
                    "[PASTE SELECTION] no items accepted");
        }

        /*
         * MOVE clears clipboard.
         * COPY keeps clipboard available.
         */
        if (operation ==
                ExplorerClipboard.Operation.MOVE) {

            clipboard.clear();
        }

        updateActionStates.run();
    }

    private boolean submitCopy(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group) {

        if (item == null
                || group == null) {

            return false;
        }

        try {

            fileOperationController.copy(
                    item,
                    targetBucket,
                    targetKey,
                    overwrite,
                    group);

            log.info(
                    "[COPY] submitted source={} target={} " +
                            "overwrite={} group={}",
                    item.getKey(),
                    targetKey,
                    overwrite,
                    group.getDisplayName());

            return true;

        } catch (Exception ex) {

            log.error(
                    "[COPY] failed source={} target={} group={}",
                    item.getKey(),
                    targetKey,
                    group.getDisplayName(),
                    ex);

            showError(
                    ex,
                    "Copy Failed");

            return false;
        }
    }

    private boolean submitMove(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group) {

        if (item == null
                || group == null) {

            return false;
        }

        try {

            fileOperationController.move(
                    item,
                    targetBucket,
                    targetKey,
                    overwrite,
                    group);

            log.info(
                    "[MOVE] submitted source={} target={} " +
                            "overwrite={} group={}",
                    item.getKey(),
                    targetKey,
                    overwrite,
                    group.getDisplayName());

            return true;

        } catch (Exception ex) {

            log.error(
                    "[MOVE] failed source={} target={} group={}",
                    item.getKey(),
                    targetKey,
                    group.getDisplayName(),
                    ex);

            showError(
                    ex,
                    "Move Failed");

            return false;
        }
    }

    private void showError(
            Exception ex,
            String title) {

        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        null,
                        ex.getMessage(),
                        title,
                        JOptionPane.ERROR_MESSAGE));
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
}