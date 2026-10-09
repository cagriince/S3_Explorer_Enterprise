package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.util.DateFormatter;
import com.company.s3explorer.util.S3Util;

import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransferTableModel
        extends AbstractTableModel {

    private static final String[] COLUMNS = {

            "Process",
            "Process Detail",
            "Size",
            "Progress",
            "Status",
            "Start/End Time",
            "Elapsed Time",
            "Summary"
    };

    private final List<TransferRuntime> runtimes =
            new ArrayList<>();

    private final List<Row> rows =
            new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(
            int column) {

        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(
            int row,
            int column) {

        if (row < 0
                || row >= rows.size()) {

            return "";
        }

        Row combinedRow =
                rows.get(row);

        if (combinedRow.isGroup()) {

            return getGroupValue(
                    combinedRow.getGroup(),
                    column);
        }

        return getTransferValue(
                runtimes.get(
                        combinedRow.getTransferModelRow()),
                column);
    }

    private Object getTransferValue(
            TransferRuntime runtime,
            int column) {

        if (runtime == null) {
            return "";
        }

        TransferTask task =
                runtime.getTask();

        if (task == null) {
            return "";
        }

        return switch (column) {

            case 0 ->
                    task.getType();

            case 1 ->
                    S3Util.getTransferPanelProcessDetail(
                            task.getType(),
                            task.getGroup() != null
                                    ? task.getGroup().getDisplayName()
                                    : null,
                            task.getRepositoryId(),
                            task.getBucket(),
                            task.getObjectKey(),
                            task.getTargetRepositoryId(),
                            task.getTargetBucket(),
                            task.getTargetObjectKey(),
                            task.getLocalPath());

            case 2 ->
                    task.getSize();

            case 3 ->
                    runtime;

            case 4 ->
                    runtime.getStatus();

            case 5 ->
                    "<html>"
                            + DateFormatter.format(
                            runtime.getStartTime())
                            + "<br/>"
                            + DateFormatter.format(
                            runtime.getEndTime())
                            + "</html>";

            case 6 ->
                    formatElapsedTime(
                            runtime);

            case 7 ->
                    runtime.getMessage();

            default ->
                    "";
        };
    }

    @Override
    public Class<?> getColumnClass(
            int column) {

        return switch (column) {

            case 0 ->
                    Object.class;

            case 1 ->
                    String.class;

            case 2 ->
                    Object.class;

            case 3 ->
                    Object.class;

            case 4 ->
                    Object.class;

            case 5 ->
                    String.class;

            case 6 ->
                    String.class;

            case 7 ->
                    String.class;

            default ->
                    Object.class;
        };
    }

    /*
     * ---------------------------------------------------------
     * NORMAL TRANSFER SNAPSHOT
     * ---------------------------------------------------------
     */

    public void setSnapshot(
            List<TransferRuntime> snapshot) {

        runtimes.clear();

        if (snapshot != null) {
            runtimes.addAll(snapshot);
        }

        rebuildTransferRows();

        fireTableDataChanged();
    }


    /*
     * ---------------------------------------------------------
     * COMBINED SNAPSHOT
     * ---------------------------------------------------------
     */

    public void setSnapshot(
            List<TransferGroupStateStore.GroupRecord> groups,
            List<TransferRuntime> transfers) {

        rows.clear();

        /*
         * Önce normal transfer snapshot'ını güncelle.
         */
        runtimes.clear();

        if (transfers != null) {
            runtimes.addAll(transfers);
        }

        /*
         * -----------------------------------------------------
         * GROUP ROWS
         * -----------------------------------------------------
         *
         * En yeni group en üstte.
         */
        if (groups != null
                && !groups.isEmpty()) {

            List<TransferGroupStateStore.GroupRecord>
                    sortedGroups =
                    new ArrayList<>(groups);

            sortedGroups.removeIf(
                    group -> group == null);

            sortedGroups.sort(
                    Comparator.comparing(
                            TransferGroupStateStore.GroupRecord::getStartTime,
                            Comparator.nullsLast(
                                    Comparator.reverseOrder())));

            for (TransferGroupStateStore.GroupRecord group :
                    sortedGroups) {

                rows.add(
                        Row.group(group));
            }
        }

        /*
         * -----------------------------------------------------
         * INDIVIDUAL TRANSFER ROWS
         * -----------------------------------------------------
         */
        for (int i = 0;
             i < runtimes.size();
             i++) {

            TransferRuntime runtime =
                    runtimes.get(i);

            if (runtime == null) {
                continue;
            }

            rows.add(
                    Row.transfer(i));
        }

        fireTableDataChanged();
    }

    private void rebuildTransferRows() {

        rows.clear();

        for (int i = 0;
             i < runtimes.size();
             i++) {

            TransferRuntime runtime =
                    runtimes.get(i);

            if (runtime == null) {
                continue;
            }

            rows.add(
                    Row.transfer(i));
        }
    }

    /*
     * ---------------------------------------------------------
     * ROW ACCESS
     * ---------------------------------------------------------
     */

    public boolean isGroupRow(
            int modelRow) {

        if (modelRow < 0
                || modelRow >= rows.size()) {

            return false;
        }

        return rows.get(modelRow).isGroup();
    }

    public TransferGroupStateStore.GroupRecord getGroup(
            int modelRow) {

        if (modelRow < 0
                || modelRow >= rows.size()) {

            return null;
        }

        Row row =
                rows.get(modelRow);

        return row.isGroup()
                ? row.getGroup()
                : null;
    }

    public TransferRuntime getRuntime(
            int modelRow) {

        if (modelRow < 0
                || modelRow >= rows.size()) {

            return null;
        }

        Row row =
                rows.get(modelRow);

        if (row.isGroup()) {
            return null;
        }

        int transferModelRow =
                row.getTransferModelRow();

        if (transferModelRow < 0
                || transferModelRow >= runtimes.size()) {

            return null;
        }

        return runtimes.get(
                transferModelRow);
    }

    /*
     * ---------------------------------------------------------
     * GROUP VALUES
     * ---------------------------------------------------------
     */

    private Object getGroupValue(
            TransferGroupStateStore.GroupRecord group,
            int column) {

        if (group == null) {
            return "";
        }

        return switch (column) {

            case 0 ->
                    group.getGroup().getOperation();

            case 1 ->
                    buildGroupProcessDetail(
                            group);

            case 2 ->
                    null;

            case 3 ->
                    new GroupProgress(
                            group.getCompleted(),
                            group.getFailedCount(),
                            group.getCancelled(),
                            group.getSkipped(),
                            group.getDetected(),
                            group.isPreparing());

            case 4 ->
                    getGroupStatus(
                            group);

            case 5 ->
                    "<html>"
                            + DateFormatter.format(
                            group.getStartTime())
                            + "<br/>"
                            + DateFormatter.format(
                            group.getEndTime())
                            + "</html>";

            case 6 ->
                    formatGroupElapsedTime(
                            group);

            case 7 ->
                    buildGroupSummary(
                            group);

            default ->
                    "";
        };
    }

    private String buildGroupProcessDetail(
            TransferGroupStateStore.GroupRecord group) {

        TransferGroup transferGroup =
                group.getGroup();

        if (transferGroup == null) {
            return "";
        }

        Path localPath = null;

        if (transferGroup.getOperation()
                == TransferType.DOWNLOAD
                || transferGroup.getOperation()
                == TransferType.DOWNLOAD_GROUP) {

            String targetPrefix =
                    transferGroup.getTargetPrefix();

            if (targetPrefix != null
                    && !targetPrefix.isBlank()) {

                localPath =
                        Path.of(targetPrefix);
            }

        } else if (transferGroup.getOperation()
                == TransferType.UPLOAD
                || transferGroup.getOperation()
                == TransferType.UPLOAD_GROUP) {

            String source =
                    transferGroup.getSource();

            if (source != null
                    && !source.isBlank()) {

                localPath =
                        Path.of(source);
            }
        }

        return S3Util.getTransferPanelProcessDetail(
                transferGroup.getOperation(),
                transferGroup.getDisplayName(),
                transferGroup.getSourceRepository(),
                transferGroup.getSourceBucket(),
                transferGroup.getSourcePrefix(),
                transferGroup.getTargetRepository(),
                transferGroup.getTargetBucket(),
                transferGroup.getTargetPrefix(),
                localPath);
    }

    private String buildGroupSummary(
            TransferGroupStateStore.GroupRecord group) {

        if (group.isPreparing()) {

            long detected =
                    group.getDetected();

            if (detected > 0) {

                return "Preparing, Discovered: "
                        + S3Util.formatWithThousandSeparator(
                        detected);
            }

            return "Preparing";
        }

        StringBuilder summary =
                new StringBuilder();

        summary.append(
                        "Discovered: ")
                .append(
                        S3Util.formatWithThousandSeparator(
                                group.getDetected()));

        summary.append(
                        ", Success: ")
                .append(
                        S3Util.formatWithThousandSeparator(
                                group.getCompleted()));

        int failed =
                group.getFailedCount();

        int cancelled =
                group.getCancelled();

        int skipped =
                group.getSkipped();

        if (failed > 0) {

            summary.append(
                            ", Failed: ")
                    .append(
                            S3Util.formatWithThousandSeparator(
                                    failed));
        }

        if (cancelled > 0) {

            summary.append(
                            ", Cancelled: ")
                    .append(
                            S3Util.formatWithThousandSeparator(
                                    cancelled));
        }

        if (skipped > 0) {

            summary.append(
                            ", Skipped: ")
                    .append(
                            S3Util.formatWithThousandSeparator(
                                    skipped));
        }

        String errorMessage =
                group.getGroup().getErrorMessage();

        if (errorMessage != null
                && !errorMessage.isBlank()) {

            summary.append(
                            ", Error: ")
                    .append(
                            errorMessage);
        }

        return summary.toString();
    }

    private TransferStatus getGroupStatus(
            TransferGroupStateStore.GroupRecord group) {

        if (group == null) {
            return null;
        }

        if (group.isFinished()) {
            return TransferStatus.COMPLETED;
        }

        return TransferStatus.RUNNING;
    }

    private String formatGroupElapsedTime(
            TransferGroupStateStore.GroupRecord group) {

        if (group == null
                || group.getStartTime() == null) {

            return "";
        }

        return S3Util.formatWithThousandSeparator(
                group.getElapsedTime())
                + " ms";
    }

    /*
     * ---------------------------------------------------------
     * TRANSFER VALUES
     * ---------------------------------------------------------
     */

    private String formatElapsedTime(
            TransferRuntime runtime) {

        if (runtime == null
                || runtime.getStartTime() == null) {

            return "";
        }

        return S3Util.formatWithThousandSeparator(
                runtime.getElapsedTime())
                + " ms";
    }

    /*
     * ---------------------------------------------------------
     * GROUP PROGRESS
     * ---------------------------------------------------------
     */

    public static final class GroupProgress {

        private final int completed;
        private final int failed;
        private final int cancelled;
        private final int skipped;

        private final long detected;

        private final boolean preparing;

        private GroupProgress(
                int completed,
                int failed,
                int cancelled,
                int skipped,
                long detected,
                boolean preparing) {

            this.completed =
                    Math.max(
                            0,
                            completed);

            this.failed =
                    Math.max(
                            0,
                            failed);

            this.cancelled =
                    Math.max(
                            0,
                            cancelled);

            this.skipped =
                    Math.max(
                            0,
                            skipped);

            this.detected =
                    Math.max(
                            0L,
                            detected);

            this.preparing =
                    preparing;
        }

        public int getPercent() {

            if (detected <= 0) {
                return 0;
            }

            long finished =
                    (long) completed
                            + failed
                            + cancelled
                            + skipped;

            long percent =
                    finished * 100L / detected;

            return (int) Math.max(
                    0L,
                    Math.min(
                            100L,
                            percent));
        }
    }

    /*
     * ---------------------------------------------------------
     * ROW
     * ---------------------------------------------------------
     */

    private static final class Row {

        private enum Type {
            GROUP,
            TRANSFER
        }

        private final Type type;

        private final
        TransferGroupStateStore.GroupRecord group;

        private final int transferModelRow;

        private Row(
                Type type,
                TransferGroupStateStore.GroupRecord group,
                int transferModelRow) {

            this.type =
                    type;

            this.group =
                    group;

            this.transferModelRow =
                    transferModelRow;
        }

        private static Row group(
                TransferGroupStateStore.GroupRecord group) {

            return new Row(
                    Type.GROUP,
                    group,
                    -1);
        }

        private static Row transfer(
                int transferModelRow) {

            return new Row(
                    Type.TRANSFER,
                    null,
                    transferModelRow);
        }

        private boolean isGroup() {
            return type == Type.GROUP;
        }

        private TransferGroupStateStore.GroupRecord getGroup() {
            return group;
        }

        private int getTransferModelRow() {
            return transferModelRow;
        }
    }
}