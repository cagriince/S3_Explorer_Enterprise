package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.ui.icons.FileIconRegistry;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FileTableModel extends AbstractTableModel {

    public static final int COL_FOLDER = 0;
    public static final int COL_NAME = 1;
    public static final int COL_TYPE = 2;
    public static final int COL_SIZE = 3;
    public static final int COL_LAST_MODIFIED = 4;

    private final List<S3FileItem> files =
            new ArrayList<>();

    private static final String[] COLUMNS = {
            "",
            "Name",
            "Type",
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
    public String getColumnName(
            int column) {

        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(
            int rowIndex,
            int columnIndex) {

        S3FileItem item =
                files.get(rowIndex);

        return switch (columnIndex) {

            case COL_FOLDER ->
                    item.isParentFolder() ? 0 : (item.isFolder() ? 1 : 2);

            case COL_NAME ->
                    item;

            case COL_TYPE -> {

                if (item.isFolder()) {
                    yield "Folder";
                }

                yield FileIconRegistry
                        .findFileType(
                                item.getKey())
                        .displayName();
            }
            
            case COL_SIZE ->
                    item.isFolder()
                            ? null
                            : item.getSize();

            case COL_LAST_MODIFIED ->
                    item.getLastModified();

            default ->
                    null;
        };
    }

    @Override
    public Class<?> getColumnClass(
            int column) {

        return switch (column) {

            case COL_FOLDER ->
                    Integer.class;

            case COL_NAME ->
                    S3FileItem.class;
            
            case COL_TYPE ->
                    String.class;
            
            case COL_SIZE ->
                    Long.class;

            case COL_LAST_MODIFIED ->
                    Instant.class;

            default ->
                    Object.class;
        };
    }

    public void setFiles(
            List<S3FileItem> newFiles) {

        files.clear();

        files.addAll(newFiles);

        fireTableDataChanged();
    }

    public void addFiles(
            List<S3FileItem> newFiles) {

        if (newFiles == null
                || newFiles.isEmpty()) {

            return;
        }

        int firstRow =
                files.size();

        files.addAll(newFiles);

        fireTableRowsInserted(
                firstRow,
                files.size() - 1);
    }

    public void clear() {
        files.clear();
    }

    public void clearAndRepaint() {

        files.clear();

        fireTableDataChanged();
    }

    public S3FileItem getItem(
            int row) {

        return files.get(row);
    }

    public List<S3FileItem> getItems() {

        return List.copyOf(files);
    }
}