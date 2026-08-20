package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileTableRowSorter extends TableRowSorter<FileTableModel> {

    public FileTableRowSorter(FileTableModel model) {
        super(model);

        Collator turkceCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkceCollator.setStrength(
                Collator.PRIMARY);

        /*
         * Klasör/Dosya:
         * klasörler önce, dosyalar sonra.
         * ".." ise klasörler içinde de en önde.
         */
        this.setComparator(
                FileTableModel.COL_FOLDER,
                (left, right) -> {

                    S3FileItem item1 =
                            (S3FileItem) left;

                    S3FileItem item2 =
                            (S3FileItem) right;

                    return compareFolderPosition(
                            item1,
                            item2);
                });

        /*
         * Name
         */
        this.setComparator(
                FileTableModel.COL_NAME,
                (left, right) -> {

                    S3FileItem item1 =
                            (S3FileItem) left;

                    S3FileItem item2 =
                            (S3FileItem) right;

                    int parentResult =
                            compareParentPosition(
                                    item1,
                                    item2);

                    if (parentResult != 0) {
                        return parentResult;
                    }

                    return turkceCollator.compare(
                            S3Util.extractFolderName(
                                    item1.getKey()),
                            S3Util.extractFolderName(
                                    item2.getKey()));
                });

        /*
         * Size
         */
        this.setComparator(
                FileTableModel.COL_SIZE,
                (left, right) -> {

                    S3FileItem item1 =
                            (S3FileItem) left;

                    S3FileItem item2 =
                            (S3FileItem) right;

                    int parentResult =
                            compareParentPosition(
                                    item1,
                                    item2);

                    if (parentResult != 0) {
                        return parentResult;
                    }

                    return Long.compare(
                            item1.getSize(),
                            item2.getSize());
                });

        /*
         * Last Modified
         */
        this.setComparator(
                FileTableModel.COL_LAST_MODIFIED,
                (left, right) -> {

                    S3FileItem item1 =
                            (S3FileItem) left;

                    S3FileItem item2 =
                            (S3FileItem) right;

                    int parentResult =
                            compareParentPosition(
                                    item1,
                                    item2);

                    if (parentResult != 0) {
                        return parentResult;
                    }

                    Instant date1 =
                            item1.getLastModified();

                    Instant date2 =
                            item2.getLastModified();

                    if (date1 == null && date2 == null) {
                        return 0;
                    }

                    if (date1 == null) {
                        return -1;
                    }

                    if (date2 == null) {
                        return 1;
                    }

                    return date1.compareTo(date2);
                });
    }

    @Override
    public void toggleSortOrder(int column) {
        // Eğer kullanıcı zaten 0. kolona (Klasör/Dosya türüne) tıkladıysa standart davranışı koru
        if (column == 0) {
            super.toggleSortOrder(column);
            return;
        }

        SortOrder yeniDuzen = SortOrder.ASCENDING;
        List<? extends SortKey> mevcutAnahtarlar = getSortKeys();

        // Listenin ilk elemanına bakmak yerine, tıklanan sütunun anahtarını arıyoruz
        for (SortKey anahtar : mevcutAnahtarlar) {
            if (anahtar.getColumn() == column) {
                // Tıklanan sütun listede bulunduysa, mevcut yönünün tersini alıyoruz
                if (anahtar.getSortOrder() == SortOrder.ASCENDING) {
                    yeniDuzen = SortOrder.DESCENDING;
                } else {
                    yeniDuzen = SortOrder.ASCENDING;
                }
                break;
            }
        }

        // Çoklu sıralama listesini oluşturuyoruz
        List<SortKey> yeniAnahtarlar = new ArrayList<>();

        // 1. Öncelik: Klasörler her zaman üstte (A'dan Z'ye sıralamada "Klasör" < "Dosya")
        yeniAnahtarlar.add(new SortKey(0, SortOrder.ASCENDING));

        // 2. Öncelik: Kullanıcının tıkladığı sütun ve yeni yönü
        yeniAnahtarlar.add(new SortKey(column, yeniDuzen));

        // Sıralamayı uygula
        setSortKeys(yeniAnahtarlar);
    }

    public int getPrimarySortColumn() {

        List<? extends SortKey> keys =
                getSortKeys();

        if (keys == null
                || keys.isEmpty()) {

            return FileTableModel.COL_NAME;
        }

        for (SortKey key : keys) {

            if (key.getColumn() != 0) {

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

            if (key.getColumn() != 0) {

                return key.getSortOrder();
            }
        }

        return SortOrder.ASCENDING;
    }

    private int compareParentPosition(
            S3FileItem item1,
            S3FileItem item2) {

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

        return 0;
    }

    private int compareFolderPosition(
            S3FileItem item1,
            S3FileItem item2) {

        boolean parent1 =
                item1.isParentFolder();

        boolean parent2 =
                item2.isParentFolder();

        /*
         * ".." her şeyden önce.
         */
        if (parent1 && !parent2) {
            return -1;
        }

        if (!parent1 && parent2) {
            return 1;
        }

        /*
         * Sonra klasörler.
         */
        if (item1.isFolder()
                && !item2.isFolder()) {
            return -1;
        }

        if (!item1.isFolder()
                && item2.isFolder()) {
            return 1;
        }

        return 0;
    }
}