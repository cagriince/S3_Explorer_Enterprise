package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.TransferType;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.model.TransferTask;
import com.company.s3explorer.ui.theme.UIThemeManager;
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
            "Start Time",
            "End Time",
            "Elapsed Time (ms)",
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

        TransferRuntime runtime = runtimes.get(row);
        TransferTask task = runtime.getTask();
        return switch (column) {

            case 0 ->
                    runtime.getTask().getType();

            case 1 ->
                    S3Util.getTransferPanelProcessDetail(task.getType(), task.getGroup() != null ? task.getGroup().getDisplayName() : null, task.getRepositoryName(), task.getBucket(), task.getObjectKey(), task.getTargetRepositoryName(), task.getTargetBucket(), task.getTargetObjectKey(), task.getLocalPath());
            
            case 2 ->
                    task.getSize();

            case 3 ->
                    runtime;

            case 4 ->
                    runtime.getStatus();

            case 5 ->
                    runtime.getStartTime();

            case 6 ->
                    runtime.getEndTime();

            case 7 ->
                    runtime.getElapsedTime();

            case 8 ->
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
                    TransferType.class;

            case 1 ->
                    String.class;

            case 2 ->
                    Long.class;

            case 3 ->
                    TransferRuntime.class;

            case 4 ->
                    TransferStatus.class;

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
            List<TransferRuntime> snapshot) {

        runtimes.clear();

        if (snapshot != null) {
            runtimes.addAll(snapshot);
        }

        fireTableDataChanged();
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