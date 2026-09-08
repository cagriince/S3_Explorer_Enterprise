package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.ui.theme.UIThemeManager;
import com.company.s3explorer.util.S3Util;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransferCombinedTableModel
        extends AbstractTableModel {

    private static final String[] COLUMNS = {

            "Process",
            "Process Detail",
            "Size",
            "Progress",
            "Status",
            "Start Time",
            "End Time",
            "Elapsed Time (ms)",
            "Summary"
    };

    private final List<Row> rows =
            new ArrayList<>();

    private final TransferTableModel transferModel;

    public TransferCombinedTableModel() {
        this(1000);
    }

    public TransferCombinedTableModel(
            int maxTransferRows) {

        if (maxTransferRows < 1) {
            throw new IllegalArgumentException(
                    "maxTransferRows must be greater than zero");
        }

        transferModel =
                new TransferTableModel(
                        maxTransferRows);
    }

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

        return transferModel.getValueAt(
                combinedRow.getTransferModelRow(),
                column);
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

            case 5, 6 ->
                    Instant.class;

            case 7 ->
                    Long.class;

            case 8 ->
                    String.class;

            default ->
                    Object.class;
        };
    }

    public void setSnapshot(
            List<TransferGroupStateStore.GroupRecord> groups,
            List<TransferRuntime> transfers) {

        rows.clear();

        /*
         * GROUP ROWS
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
         * INDIVIDUAL TRANSFER ROWS
         *
         * TransferStateStore snapshot sırası
         * aynen korunur.
         */
        transferModel.setSnapshot(
                transfers);

        if (transfers != null
                && !transfers.isEmpty()) {

            for (int i = 0;
                 i < transfers.size();
                 i++) {

                TransferRuntime runtime =
                        transfers.get(i);

                if (runtime == null) {
                    continue;
                }

                rows.add(
                        Row.transfer(i));
            }
        }

        fireTableDataChanged();
    }

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

        return transferModel.getRuntime(
                row.getTransferModelRow());
    }

    public int getTransferModelRow(
            int modelRow) {

        if (modelRow < 0
                || modelRow >= rows.size()) {

            return -1;
        }

        Row row =
                rows.get(modelRow);

        if (row.isGroup()) {
            return -1;
        }

        return row.getTransferModelRow();
    }

    public boolean isTransferRow(
            int modelRow) {

        return !isGroupRow(modelRow);
    }

    private Object getGroupValue(
            TransferGroupStateStore.GroupRecord group,
            int column) {

        if (group == null) {
            return "";
        }

        switch (column) {

            /*
             * Process
             */
            case 0:
                return group.getGroup().getOperation();

            /*
             * Process Detail
             *
             * Burada TransferTableModel'deki renkli
             * HTML mantığı kullanılır.
             */
            case 1:
                return buildGroupProcessDetail(group);

            /*
             * Size
             */
            case 2:

                return null;

            /*
             * Progress
             *
             * Artık sayı göstermiyoruz.
             * Renderer yalnızca yüzde gösterecek.
             */
            case 3:

                return new GroupProgress(
                        group.getCompleted(),
                        group.getDetected(),
                        group.isPreparing());

            /*
             * Status
             */
            case 4:

                return getGroupStatus(
                        group);

            /*
             * Start Time
             */
            case 5:

                return group.getStartTime();

            /*
             * End Time
             */
            case 6:

                return group.getEndTime();

            /*
             * Elapsed Time
             */
            case 7:

                return group.getElapsedTime();

            /*
             * Summary
             *
             * Progress bar'ın tekrarını yapmıyoruz.
             * Burada lifecycle bilgisi gösteriyoruz.
             */
            case 8:

                return buildGroupSummary(
                        group);

            default:

                return "";
        }
    }

    private String buildGroupProcessDetail(TransferGroupStateStore.GroupRecord group) {
        TransferGroup transferGroup = group.getGroup();
        if (transferGroup == null) {
            return "";
        }
        
        return S3Util.getTransferPanelProcessDetail(transferGroup.getOperation(), transferGroup.getDisplayName(), transferGroup.getSourceRepository(), transferGroup.getSourceBucket(), transferGroup.getSourcePrefix(), transferGroup.getTargetRepository(), transferGroup.getTargetBucket(), transferGroup.getTargetPrefix(), null);
    }

    private String buildGroupSummary(
            TransferGroupStateStore.GroupRecord group) {

        if (group.isPreparing()) {

            long detected =
                    group.getDetected();

            if (detected > 0) {

                return "Preparing, Detected: "
                        + detected;
            }

            return "Preparing";
        }

        StringBuilder summary =
                new StringBuilder();

        summary.append(
                        "Detected: ")
                .append(
                        group.getDetected());

        summary.append(
                        ", Completed: ")
                .append(
                        group.getCompleted());

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
                            failed);
        }

        if (cancelled > 0) {

            summary.append(
                            ", Cancelled: ")
                    .append(
                            cancelled);
        }

        if (skipped > 0) {

            summary.append(
                            ", Skipped: ")
                    .append(
                            skipped);
        }

        return summary.toString();
    }

    private String getGroupStatus(
            TransferGroupStateStore.GroupRecord group) {

        if (group.isFinished()) {

            if (group.isFailed()) {
                return "Failed";
            }

            return "Finished";
        }

        if (group.isPreparing()) {
            return "Preparing";
        }

        if (group.isRunning()) {
            return "Running";
        }

        return "Preparing";
    }

    private String safe(
            String value) {

        return value != null
                ? value
                : "";
    }

    public static final class GroupProgress {

        private final int completed;
        private final long detected;
        private final boolean preparing;

        private GroupProgress(
                int completed,
                long detected,
                boolean preparing) {

            this.completed =
                    Math.max(
                            0,
                            completed);

            this.detected =
                    Math.max(
                            0L,
                            detected);

            this.preparing =
                    preparing;
        }

        public int getCompleted() {
            return completed;
        }

        public long getDetected() {
            return detected;
        }

        public boolean isPreparing() {
            return preparing;
        }

        public int getPercent() {

            if (detected <= 0) {
                return 0;
            }

            long percent =
                    completed * 100L / detected;

            return (int) Math.max(
                    0L,
                    Math.min(
                            100L,
                            percent));
        }

        public String getText() {

            return getPercent() + "%";
        }
    }

    private static final class Row {

        private enum Type {
            GROUP,
            TRANSFER
        }

        private final Type type;

        private final TransferGroupStateStore.GroupRecord group;

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