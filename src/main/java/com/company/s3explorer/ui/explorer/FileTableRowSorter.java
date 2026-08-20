package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;

import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileTableRowSorter
        extends TableRowSorter<FileTableModel> {

    public FileTableRowSorter(
            FileTableModel model) {

        super(model);

        Collator turkceCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkceCollator.setStrength(
                Collator.PRIMARY);

        /*
         * ---------------------------------------------
         * FOLDER / FILE
         * ---------------------------------------------
         *
         * FileTableModel.COL_FOLDER -> Integer
         *
         * 0  = parent folder (..)
         * 1 = folder
         * 2 ? file
         * 
         * Klasörler önce gelsin.
         */
        this.setComparator(
                FileTableModel.COL_FOLDER,
                Comparator.reverseOrder());

        /*
         * ---------------------------------------------
         * NAME
         * ---------------------------------------------
         *
         * COL_NAME -> S3FileItem
         */
        this.setComparator(
                FileTableModel.COL_NAME,
                (left, right) -> {

                    S3FileItem item1 =
                            (S3FileItem) left;

                    S3FileItem item2 =
                            (S3FileItem) right;

                    /*
                     * ".." parent folder
                     * her zaman önce.
                     */
                    boolean parent1 =
                            item1.isParentFolder();

                    boolean parent2 =
                            item2.isParentFolder();

                    if (parent1 && !parent2) {
                        return -1;
                    }

                    if (!parent1 && parent2) {
                        return 1;
                    }

                    return turkceCollator.compare(
                            S3Util.extractFolderName(
                                    item1.getKey()),
                            S3Util.extractFolderName(
                                    item2.getKey()));
                });

        /*
         * ---------------------------------------------
         * SIZE
         * ---------------------------------------------
         *
         * COL_SIZE -> Long
         */
        this.setComparator(
                FileTableModel.COL_SIZE,
                Comparator.nullsFirst(
                        Long::compare));

        /*
         * ---------------------------------------------
         * LAST MODIFIED
         * ---------------------------------------------
         *
         * COL_LAST_MODIFIED -> Instant
         */
        this.setComparator(
                FileTableModel.COL_LAST_MODIFIED,
                Comparator.nullsFirst(
                        Instant::compareTo));
    }

    @Override
    public void toggleSortOrder(
            int column) {

        /*
         * Folder/File kolonuna basılırsa
         * mevcut Swing davranışını koru.
         */
        if (column ==
                FileTableModel.COL_FOLDER) {

            super.toggleSortOrder(column);

            return;
        }

        SortOrder yeniDuzen =
                SortOrder.ASCENDING;

        List<? extends SortKey>
                mevcutAnahtarlar =
                getSortKeys();

        /*
         * Tıklanan kolonun mevcut yönünü bul.
         */
        for (SortKey anahtar :
                mevcutAnahtarlar) {

            if (anahtar.getColumn()
                    == column) {

                if (anahtar.getSortOrder()
                        == SortOrder.ASCENDING) {

                    yeniDuzen =
                            SortOrder.DESCENDING;

                } else {

                    yeniDuzen =
                            SortOrder.ASCENDING;
                }

                break;
            }
        }

        List<SortKey> yeniAnahtarlar =
                new ArrayList<>();

        /*
         * Klasörler her zaman dosyalardan önce.
         *
         */
        yeniAnahtarlar.add(
                new SortKey(
                        FileTableModel.COL_FOLDER,
                        SortOrder.DESCENDING));

        /*
         * Kullanıcının seçtiği kolon.
         */
        yeniAnahtarlar.add(
                new SortKey(
                        column,
                        yeniDuzen));

        setSortKeys(
                yeniAnahtarlar);
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
}