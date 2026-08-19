package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.util.S3Util;

import java.text.Collator;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

public final class FileTableSortSpec {

    public enum Column {
        NAME,
        SIZE,
        LAST_MODIFIED
    }

    private final Column column;
    private final boolean ascending;

    public FileTableSortSpec(
            Column column,
            boolean ascending) {

        this.column = column;
        this.ascending = ascending;
    }

    public Column getColumn() {
        return column;
    }

    public boolean isAscending() {
        return ascending;
    }

    public Comparator<S3FileItem> createFileComparator() {

        Collator turkishCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkishCollator.setStrength(
                Collator.PRIMARY);

        Comparator<S3FileItem> comparator;

        switch (column) {

            case SIZE:

                comparator =
                        Comparator.comparingLong(
                                S3FileItem::getSize);

                break;

            case LAST_MODIFIED:

                comparator =
                        Comparator.comparing(
                                S3FileItem::getLastModified,
                                Comparator.nullsFirst(
                                        Instant::compareTo));

                break;

            case NAME:
            default:

                comparator =
                        Comparator.comparing(
                                item ->
                                        S3Util.extractFolderName(
                                                item.getKey()),
                                turkishCollator);

                break;
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    public static FileTableSortSpec defaultSpec() {

        return new FileTableSortSpec(
                Column.NAME,
                true);
    }
}