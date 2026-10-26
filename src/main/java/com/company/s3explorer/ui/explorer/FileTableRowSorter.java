
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;

import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;
import java.text.CollationKey;
import java.text.Collator;
import java.time.Instant;
import java.util.*;

public class FileTableRowSorter
        extends TableRowSorter<FileTableModel> {

    private final Map<Character, CollationKey> characterKeyCache =
            new HashMap<>();

    public FileTableRowSorter(FileTableModel model) {
        super(model);

        setSortsOnUpdates(true);

        Collator turkishCollator =
                Collator.getInstance(new Locale("tr", "TR"));

        turkishCollator.setStrength(Collator.PRIMARY);

        setComparator(
                FileTableModel.COL_FOLDER,
                Comparator.nullsFirst(Integer::compareTo));

        setComparator(
                FileTableModel.COL_NAME,
                (left, right) ->
                        S3Util.naturalTurkishCompare(
                                ((S3FileItem) left).getDisplayName(),
                                ((S3FileItem) right).getDisplayName(),
                                turkishCollator,
                                characterKeyCache));

        /*
         * Type values are strings and usually repeat across many rows.
         * Avoid the default String comparator for this large table.
         */
        setComparator(
                FileTableModel.COL_TYPE,
                Comparator.nullsFirst(
                        String.CASE_INSENSITIVE_ORDER));

        setComparator(
                FileTableModel.COL_SIZE,
                Comparator.nullsFirst(Long::compareTo));

        setComparator(
                FileTableModel.COL_LAST_MODIFIED,
                Comparator.nullsFirst(Instant::compareTo));
    }

    @Override
    public void toggleSortOrder(int column) {
        long startNanos = System.nanoTime();

        try {
            if (column == FileTableModel.COL_FOLDER) {
                super.toggleSortOrder(column);
                return;
            }

            SortOrder newOrder = SortOrder.ASCENDING;

            List<? extends SortKey> currentKeys = getSortKeys();

            for (SortKey key : currentKeys) {
                if (key.getColumn() == column) {
                    if (key.getSortOrder() == SortOrder.ASCENDING) {
                        newOrder = SortOrder.DESCENDING;
                    } else {
                        newOrder = SortOrder.ASCENDING;
                    }
                    break;
                }
            }

            List<SortKey> newKeys = new ArrayList<>();

            // Keep folders before files.
            newKeys.add(
                    new SortKey(
                            FileTableModel.COL_FOLDER,
                            SortOrder.ASCENDING));

            // Apply the requested sort column and direction.
            newKeys.add(new SortKey(column, newOrder));

            setSortKeys(newKeys);

        } finally {
            long elapsedMillis =
                    (System.nanoTime() - startNanos) / 1_000_000;

            System.out.printf(
                    "[SORT TIMING] toggleSortOrder: column=%d, rows=%d, elapsed=%d ms%n",
                    column,
                    getModel().getRowCount(),
                    elapsedMillis);
        }
    }

    public int getPrimarySortColumn() {
        List<? extends SortKey> keys = getSortKeys();

        if (keys == null || keys.isEmpty()) {
            return FileTableModel.COL_NAME;
        }

        for (SortKey key : keys) {
            if (key.getColumn() != FileTableModel.COL_FOLDER) {
                return key.getColumn();
            }
        }

        return FileTableModel.COL_NAME;
    }

    public SortOrder getPrimarySortOrder() {
        List<? extends SortKey> keys = getSortKeys();

        if (keys == null || keys.isEmpty()) {
            return SortOrder.ASCENDING;
        }

        for (SortKey key : keys) {
            if (key.getColumn() != FileTableModel.COL_FOLDER) {
                return key.getSortOrder();
            }
        }

        return SortOrder.ASCENDING;
    }

    @Override
    public void allRowsChanged() {
        long startNanos = System.nanoTime();

        try {
            super.allRowsChanged();
        } finally {
            long elapsedMillis =
                    (System.nanoTime() - startNanos) / 1_000_000;

            System.out.printf(
                    "[SORT TIMING] allRowsChanged: rows=%d, elapsed=%d ms%n",
                    getModel().getRowCount(),
                    elapsedMillis);
        }
    }
}