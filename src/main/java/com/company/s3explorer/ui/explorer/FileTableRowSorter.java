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

        Collator turkceCollator = Collator.getInstance(new Locale("tr", "TR"));
        turkceCollator.setStrength(Collator.PRIMARY);

        this.setComparator(
                FileTableModel.COL_FOLDER,
                Comparator.reverseOrder());
        this.setComparator(FileTableModel.COL_NAME,
                Comparator.comparing(
                        item -> S3Util.extractFolderName(((S3FileItem) item).getKey()),
                        turkceCollator));
        this.setComparator(
                FileTableModel.COL_SIZE,
                Comparator.nullsFirst(Long::compareTo));
        this.setComparator(
                FileTableModel.COL_LAST_MODIFIED,
                Comparator.nullsFirst(Instant::compareTo));
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
}