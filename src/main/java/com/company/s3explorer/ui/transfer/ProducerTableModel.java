package com.company.s3explorer.ui.transfer;

import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;

public class ProducerTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Process",
            "Progress",
            "Status",
            "Start Time",
            "End Time",
            "Elapsed Time (ms)",
            "Message"
    };

    private ProducerRuntime runtime;

    @Override
    public int getRowCount() {
        return runtime == null ? 0 : 1;
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0 -> String.class;
            case 1 -> ProducerRuntime.class;
            case 2 -> TransferStatus.class;
            case 3 -> Instant.class;
            case 4 -> Instant.class;
            case 5 -> Long.class;
            case 6 -> String.class;
            default -> Object.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        if (runtime == null || row != 0) {
            return "";
        }

        return switch (column) {
            case 0 -> runtime.getDescription();
            case 1 -> runtime;
            case 2 -> runtime.getStatus();
            case 3 -> runtime.getStartTime();
            case 4 -> runtime.getEndTime();
            case 5 -> runtime.getElapsedTime();
            case 6 -> runtime.getMessage();
            default -> "";
        };
    }

    public ProducerRuntime getRuntime() {
        return runtime;
    }

    public void update(ProducerRuntime runtime) {
        this.runtime = runtime;

        fireTableDataChanged();
    }

    public void clear() {
        if (runtime == null) {
            return;
        }

        runtime = null;
        fireTableDataChanged();
    }

    public boolean hasRuntime() {
        return runtime != null;
    }

    public boolean isActive() {
        return runtime != null
                && runtime.getStatus() != null
                && runtime.getStatus().isActive();
    }
}