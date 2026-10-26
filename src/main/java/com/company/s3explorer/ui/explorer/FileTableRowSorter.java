
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;

import javax.swing.SortOrder;
import javax.swing.RowSorter;
import javax.swing.table.TableRowSorter;
import java.text.CollationKey;
import java.text.Collator;
import java.time.Instant;
import java.util.*;

public class FileTableRowSorter
        extends TableRowSorter<FileTableModel> {

    private final Map<Character, CollationKey> characterKeyCache =
            new HashMap<>();

    /*
     * Sort keys requested by the user but not yet applied to the table.
     * This prevents Swing from sorting the current (possibly limited)
     * rows before the background reload has prepared the correct rows.
     */
    private List<SortKey> pendingSortKeys;

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

            List<? extends SortKey> currentKeys =
                    getRequestedSortKeys();

            SortOrder newOrder = SortOrder.ASCENDING;

            for (SortKey key : currentKeys) {
                if (key.getColumn() == column) {
                    newOrder =
                            key.getSortOrder() == SortOrder.ASCENDING
                                    ? SortOrder.DESCENDING
                                    : SortOrder.ASCENDING;
                    break;
                }
            }

            List<SortKey> newKeys = new ArrayList<>();

            // Keep folders before files.
            newKeys.add(
                    new SortKey(
                            FileTableModel.COL_FOLDER,
                            SortOrder.ASCENDING));

            // Record the requested sort without sorting current rows.
            newKeys.add(new SortKey(column, newOrder));

            pendingSortKeys = List.copyOf(newKeys);

        } finally {
            long elapsedMillis =
                    (System.nanoTime() - startNanos) / 1_000_000;

            System.out.printf(
                    "[SORT TIMING] sort request recorded: "
                            + "column=%d, rows=%d, elapsed=%d ms, pending=%s%n",
                    column,
                    getModel().getRowCount(),
                    elapsedMillis,
                    pendingSortKeys != null);
        }
    }

    /**
     * Returns the pending sort request if one exists; otherwise returns
     * the sort keys currently applied to the table.
     */
    public List<? extends SortKey> getRequestedSortKeys() {
        if (pendingSortKeys != null) {
            return pendingSortKeys;
        }

        return getSortKeys();
    }

    public boolean hasPendingSortRequest() {
        return pendingSortKeys != null;
    }

    /**
     * Applies the most recent requested sort keys and clears the request.
     */
    public void applyPendingSortKeys() {
        if (pendingSortKeys == null) {
            return;
        }

        List<SortKey> keysToApply = pendingSortKeys;
        pendingSortKeys = null;

        setSortKeys(keysToApply);
    }

    public int getPrimarySortColumn() {
        List<? extends SortKey> keys = getRequestedSortKeys();

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
        List<? extends SortKey> keys = getRequestedSortKeys();

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
            /*
             * If a sort request is pending, the model has now changed.
             * Apply the requested sort to the new rows directly instead
             * of sorting once with the old keys and then sorting again.
             */
            if (pendingSortKeys != null) {
                List<SortKey> keysToApply = pendingSortKeys;
                pendingSortKeys = null;

                setSortKeys(keysToApply);
                return;
            }

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
