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

    public FileTableRowSorter(
            FileTableModel model) {

        super(model);

        /*
         * ÖNEMLİ:
         *
         * FileTableModel'de rename / replace işlemi
         *
         *     fireTableRowsUpdated(...)
         *
         * gönderiyor.
         *
         * Sorter'ın bu update sonrasında satırı mevcut
         * sıralama kriterine göre yeniden konumlandırması gerekir.
         *
         * Varsayılan Swing davranışında sortsOnUpdates false'tur.
         */
        setSortsOnUpdates(true);

        Collator turkishCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkishCollator.setStrength(
                Collator.PRIMARY);

        this.setComparator(
                FileTableModel.COL_FOLDER,
                Comparator.nullsFirst(
                        Integer::compareTo));

        this.setComparator(
                FileTableModel.COL_NAME,
                (left, right) ->
                        S3Util.naturalTurkishCompare(
                                ((S3FileItem) left).getDisplayName(),
                                ((S3FileItem) right).getDisplayName(),
                                turkishCollator,
                                characterKeyCache));
        /*        Comparator.comparing(
                        item ->
                                S3Util.extractFolderName(
                                        ((S3FileItem) item).getKey()),
                        turkishCollator)*/

        this.setComparator(
                FileTableModel.COL_SIZE,
                Comparator.nullsFirst(
                        Long::compareTo));

        this.setComparator(
                FileTableModel.COL_LAST_MODIFIED,
                Comparator.nullsFirst(
                        Instant::compareTo));
    }


    @Override
    public void toggleSortOrder(int column) {
        long startNanos = System.nanoTime();

        try {
            /*
             * Folder/File kolonuna basılırsa
             * mevcut Swing davranışını koru.
             */
            if (column == FileTableModel.COL_FOLDER) {
                super.toggleSortOrder(column);
                return;
            }

            SortOrder yeniDuzen = SortOrder.ASCENDING;

            List<? extends SortKey> mevcutAnahtarlar = getSortKeys();

            /*
             * Tıklanan kolonun mevcut yönünü bul.
             */
            for (SortKey anahtar : mevcutAnahtarlar) {
                if (anahtar.getColumn() == column) {
                    if (anahtar.getSortOrder() == SortOrder.ASCENDING) {
                        yeniDuzen = SortOrder.DESCENDING;
                    } else {
                        yeniDuzen = SortOrder.ASCENDING;
                    }
                    break;
                }
            }

            List<SortKey> yeniAnahtarlar = new ArrayList<>();

            /*
             * Klasörler her zaman dosyalardan önce.
             */
            yeniAnahtarlar.add(
                    new SortKey(
                            FileTableModel.COL_FOLDER,
                            SortOrder.ASCENDING));

            /*
             * Kullanıcının seçtiği kolon.
             */
            yeniAnahtarlar.add(
                    new SortKey(column, yeniDuzen));

            setSortKeys(yeniAnahtarlar);

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

        List<? extends SortKey> keys =
                getSortKeys();

        if (keys == null
                || keys.isEmpty()) {

            return FileTableModel.COL_NAME;
        }

        for (SortKey key : keys) {

            if (key.getColumn()
                    != FileTableModel.COL_FOLDER) {

                return key.getColumn();
            }
        }

        return FileTableModel.COL_NAME;
    }

    public SortOrder getPrimarySortOrder() {

        List<? extends SortKey> keys =
                getSortKeys();

        if (keys == null
                || keys.isEmpty()) {

            return SortOrder.ASCENDING;
        }

        for (SortKey key : keys) {

            if (key.getColumn()
                    != FileTableModel.COL_FOLDER) {

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