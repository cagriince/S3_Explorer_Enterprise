package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.repository.RepositoryDefinition;
import com.company.s3explorer.security.CrossRepositoryEncryptionOptions;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.manager.TransferManager;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.util.S3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

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

    private final Function<String, RepositoryDefinition>
            repositoryLookup;

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
            Runnable updateActionStates,
            Function<String, RepositoryDefinition> repositoryLookup) {

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

        this.repositoryLookup =
                repositoryLookup;
    }


    public void paste() {

        if (clipboard.isEmpty()) {
            return;
        }

        String targetBucket =
                currentBucketSupplier.get();

        String targetPrefix =
                currentPrefixSupplier.get();

        RepositoryDefinition targetRepository =
                currentRepositorySupplier.get();

        if (targetBucket == null
                || targetPrefix == null
                || targetRepository == null
                || targetRepository.isEmpty()) {
            return;
        }

        List<S3FileItem> items =
                new ArrayList<>(clipboard.getItems());

        ExplorerClipboard.Operation operation =
                clipboard.getOperation();

        if (items.isEmpty() || operation == null) {
            return;
        }

        CrossRepositoryEncryptionOptions encryptionOptions =
                CrossRepositoryEncryptionOptions.none();

        S3FileItem firstItem = items.stream()
                .filter(item -> item != null && !item.isParentFolder())
                .findFirst()
                .orElse(null);

        if (firstItem != null
                && !targetRepository.getId().equals(
                firstItem.getRepositoryId())) {

            RepositoryDefinition sourceRepository =
                    repositoryLookup.apply(
                            firstItem.getRepositoryId());

            if (sourceRepository == null
                    || sourceRepository.isEmpty()) {

                JOptionPane.showMessageDialog(
                        null,
                        "The source repository could not be found.",
                        "Copy / Move",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean encryptionApplies =
                    sourceRepository.hasEncryptionConfiguration()
                            || targetRepository.hasEncryptionConfiguration();

            if (encryptionApplies) {

                encryptionOptions =
                        CrossRepositoryEncryptionDialog.showDialog(
                                null,
                                sourceRepository,
                                targetRepository);

                // Cancel: no transfer tasks should be submitted.
                if (encryptionOptions == null) {
                    return;
                }
            }
        }

        executePaste(
                targetBucket,
                targetPrefix,
                items,
                operation,
                encryptionOptions);
    }


    private void executePaste(
            String targetBucket,
            String targetPrefix,
            List<S3FileItem> items,
            ExplorerClipboard.Operation operation,
            CrossRepositoryEncryptionOptions encryptionOptions) {

        if (targetBucket == null
                || targetPrefix == null
                || items == null
                || items.isEmpty()
                || operation == null) {
            return;
        }

        pasteSelectionProgressConsumer.accept(true);

        List<String> selectionKeys = new ArrayList<>();

        TransferGroup group = null;
        int skippedCount = 0;
        boolean folderProducerSubmitted = false;

        long validItemCount = items.stream()
                .filter(item -> item != null && !item.isParentFolder())
                .count();

        boolean requiresGroup =
                validItemCount > 1
                        || items.stream().anyMatch(
                        item -> item != null && item.isFolder());

        for (S3FileItem item : items) {
            if (item == null || item.isParentFolder()) {
                continue;
            }

            String targetSubmissionKey;
            String targetSelectionKey;

            if (item.isFolder()) {
                targetSubmissionKey = targetPrefix;

                targetSelectionKey =
                        S3Util.combineKey(targetPrefix, item.getName());

                if (!targetSelectionKey.endsWith("/")) {
                    targetSelectionKey += "/";
                }
            } else {
                targetSubmissionKey =
                        S3Util.combineKey(targetPrefix, item.getName());

                targetSelectionKey = targetSubmissionKey;
            }

            // Do not paste an item onto itself.
            if (item.getBucket().equals(targetBucket)
                    && item.getKey().equals(targetSelectionKey)) {

                log.info(
                        "[PASTE] skipped self-target source={} target={}",
                        item.getKey(),
                        targetSelectionKey);
                continue;
            }

            boolean overwrite = false;

            // Folder operations merge; file operations ask before overwrite.
            if (!item.isFolder()
                    && existsPredicate.test(targetSubmissionKey)) {

                if (!conflictConfirmation.test(item, targetSubmissionKey)) {
                    if (group != null) {
                        group.skipped();
                    } else {
                        skippedCount++;
                    }

                    log.info(
                            operation == ExplorerClipboard.Operation.COPY
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
             * Yalnızca birden fazla öğe veya klasör işlemi için grup oluştur.
             * Tek dosyalı Copy/Move grupsuz devam eder.
             */
            if (group == null && requiresGroup) {
                String groupName = S3Util.getOperationGroupName(items);

                String sourcePrefix = item.isFolder()
                        ? item.getKey()
                        : S3Util.extractParentPrefix(item.getKey());

                TransferType groupOperation =
                        operation == ExplorerClipboard.Operation.COPY
                                ? TransferType.COPY_GROUP
                                : TransferType.MOVE_GROUP;

                RepositoryDefinition repository =
                        currentRepositorySupplier.get();

                if (repository == null) {
                    pasteSelectionProgressConsumer.accept(false);
                    return;
                }

                group = transferManager.createOperationGroup(
                        groupOperation,
                        groupName,
                        item.getRepositoryId(),
                        item.getBucket(),
                        sourcePrefix,
                        repository.getId(),
                        targetBucket,
                        targetPrefix);

                group.setCrossRepositoryEncryptionOptions(encryptionOptions);

                transferManager.configureGroupCompletion(
                        group,
                        item.getRepositoryId(),
                        item.getBucket(),
                        sourcePrefix,
                        operation == ExplorerClipboard.Operation.MOVE);

                log.info(
                        "[PASTE GROUP] created operation={} group={} sourcePrefix={} targetPrefix={}",
                        groupOperation,
                        group.getDisplayName(),
                        sourcePrefix,
                        targetPrefix);

                if (skippedCount > 0) {
                    for (int i = 0; i < skippedCount; i++) {
                        group.skipped();
                    }
                    skippedCount = 0;
                }
            }

            boolean submitted;

            if (operation == ExplorerClipboard.Operation.COPY) {
                submitted = submitCopy(
                        item,
                        targetBucket,
                        targetSubmissionKey,
                        overwrite,
                        group,
                        encryptionOptions);
            } else {
                submitted = submitMove(
                        item,
                        targetBucket,
                        targetSubmissionKey,
                        overwrite,
                        group,
                        encryptionOptions);
            }

            if (submitted) {
                selectionKeys.add(targetSelectionKey);

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

        // File-only grouped operations finish production here.
        // Folder producers own their group's production lifecycle.
        if (group != null && !folderProducerSubmitted) {
            group.markProductionCompleted();
        }

        if (!selectionKeys.isEmpty()) {
            pendingSelectionConsumer.accept(new ArrayList<>(selectionKeys));
            restoreFocusConsumer.accept(true);
            forceFocusConsumer.accept(true);
        } else {
            pendingSelectionConsumer.accept(null);
        }

        // MOVE clears clipboard; COPY keeps it available.
        if (operation == ExplorerClipboard.Operation.MOVE) {
            clipboard.clear();
        }

        updateActionStates.run();
    }


    private boolean submitCopy(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group,
            CrossRepositoryEncryptionOptions options) {

        if (item == null) {
            return false;
        }

        try {
            fileOperationController.copy(
                    item,
                    targetBucket,
                    targetKey,
                    overwrite,
                    group,
                    options);

            log.info(
                    "[COPY] submitted source={} target={} overwrite={} group={}",
                    item.getKey(),
                    targetKey,
                    overwrite,
                    group == null ? null : group.getDisplayName());

            return true;

        } catch (Exception ex) {
            log.error(
                    "[COPY] failed source={} target={} group={}",
                    item.getKey(),
                    targetKey,
                    group == null ? null : group.getDisplayName(),
                    ex);

            showError(ex, "Copy Failed");
            return false;
        }
    }


    private boolean submitMove(
            S3FileItem item,
            String targetBucket,
            String targetKey,
            boolean overwrite,
            TransferGroup group,
            CrossRepositoryEncryptionOptions options) {

        if (item == null) {
            return false;
        }

        try {
            fileOperationController.move(
                    item,
                    targetBucket,
                    targetKey,
                    overwrite,
                    group,
                    options);

            log.info(
                    "[MOVE] submitted source={} target={} overwrite={} group={}",
                    item.getKey(),
                    targetKey,
                    overwrite,
                    group == null ? null : group.getDisplayName());

            return true;

        } catch (Exception ex) {
            log.error(
                    "[MOVE] failed source={} target={} group={}",
                    item.getKey(),
                    targetKey,
                    group == null ? null : group.getDisplayName(),
                    ex);

            showError(ex, "Move Failed");
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
}