package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.util.DateFormatter;
import com.company.s3explorer.util.S3Util;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
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
            "Error Message"
    };

    private final List<TransferRuntime> runtimes =
            new ArrayList<>();

    private final int maxRows;

    public TransferTableModel() {
        this(1000);
    }

    public TransferTableModel(
            int maxRows) {

        if (maxRows < 1) {
            throw new IllegalArgumentException(
                    "maxRows must be greater than zero");
        }

        this.maxRows = maxRows;
    }

    @Override
    public int getRowCount() {
        return runtimes.size();
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
                || row >= runtimes.size()) {

            return "";
        }

        TransferRuntime runtime =
                runtimes.get(row);

        TransferTask task =
                runtime.getTask();

        return switch (column) {

            case 0 ->
                    runtime.getTask().getType();

            case 1 ->
                    S3Util.getTransferPanelProcessDetail(
                            task.getType(),
                            task.getGroup() != null
                                    ? task.getGroup().getDisplayName()
                                    : null,
                            task.getRepositoryName(),
                            task.getBucket(),
                            task.getObjectKey(),
                            task.getTargetRepositoryName(),
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
                    formatElapsedTime(runtime);

            case 7 ->
                    runtime.getMessage();

            default ->
                    "";
        };
    }

    private String formatElapsedTime(
            TransferRuntime runtime) {

        if (runtime == null
                || runtime.getStartTime() == null) {

            return "";
        }

        return runtime.getElapsedTime()
                + " ms";
    }

    @Override
    public Class<?> getColumnClass(
            int column) {

        return switch (column) {

            case 0 ->
                    TransferType.class;

            case 1 ->
                    String.class;

            case 2 ->
                    Long.class;

            case 3 ->
                    TransferRuntime.class;

            case 4 ->
                    TransferStatus.class;

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

    public void setSnapshot(
            List<TransferRuntime> snapshot) {

        if (isSameSnapshot(snapshot)) {
            return;
        }

        runtimes.clear();

        if (snapshot != null) {
            runtimes.addAll(snapshot);
        }

        fireTableDataChanged();
    }

    private boolean isSameSnapshot(
            List<TransferRuntime> snapshot) {

        if (snapshot == null) {
            return runtimes.isEmpty();
        }

        if (runtimes.size() != snapshot.size()) {
            return false;
        }

        for (int i = 0;
             i < runtimes.size();
             i++) {

            TransferRuntime current =
                    runtimes.get(i);

            TransferRuntime incoming =
                    snapshot.get(i);

            if (current == null
                    || incoming == null) {

                if (current != incoming) {
                    return false;
                }

                continue;
            }

            if (current.getTask() == null
                    || incoming.getTask() == null) {

                if (current.getTask()
                        != incoming.getTask()) {

                    return false;
                }

                continue;
            }

            if (current.getTask().getId() == null
                    || incoming.getTask().getId() == null) {

                if (current.getTask().getId()
                        != incoming.getTask().getId()) {

                    return false;
                }

                continue;
            }

            if (!current.getTask()
                    .getId()
                    .equals(
                            incoming.getTask().getId())) {

                return false;
            }

            if (current.getStatus()
                    != incoming.getStatus()) {

                return false;
            }
        }

        return true;
    }

    public TransferRuntime getRuntime(
            int row) {

        if (row < 0
                || row >= runtimes.size()) {

            return null;
        }

        return runtimes.get(row);
    }

    public TransferRuntime getRuntimeAtModelRow(
            int modelRow) {

        return getRuntime(modelRow);
    }

    public int getMaxRows() {
        return maxRows;
    }
}