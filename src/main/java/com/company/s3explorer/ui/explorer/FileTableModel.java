package com.company.s3explorer.ui.explorer;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FileTableModel extends AbstractTableModel {
    public static final int COL_FOLDER = 0;
    public static final int COL_NAME = 1;
    public static final int COL_SIZE = 2;
    public static final int COL_LAST_MODIFIED = 3;

    private final List<S3FileItem> files = new ArrayList<>();

    private static final String[] COLUMNS = {
            "",
            "Name",
            "Size",
            "Last Modified"
    };

    @Override
    public int getRowCount() {
        return files.size();
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        S3FileItem item = files.get(rowIndex);
        return switch (columnIndex) {
            case COL_FOLDER -> item.isFolder();
            case COL_NAME -> item;
            case COL_SIZE -> item.isFolder() ? null : item.getSize();
            case COL_LAST_MODIFIED -> item.getLastModified();
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0 -> Boolean.class;
            case 1 -> S3FileItem.class;
            case 2 -> Long.class;
            case 3 -> Instant.class;
            default -> Object.class;
        };
    }

    public void setFiles(List<S3FileItem> newFiles) {
        this.clear();
        files.addAll(newFiles);
        fireTableDataChanged();
    }

    private void clear() {
        files.clear();
    }

    public void clearAndRepaint() {
        this.clear();
        fireTableDataChanged();
    }

    public S3FileItem getItem(int row) {
        return files.get(row);
    }

    public List<S3FileItem> getItems() {
        return files;
    }
}