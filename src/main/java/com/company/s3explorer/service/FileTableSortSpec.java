package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

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

    public Comparator<S3Object>
    createFileComparator() {

        Collator collator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        collator.setStrength(
                Collator.PRIMARY);

        Comparator<S3Object> comparator;

        switch (column) {

            case SIZE:

                comparator =
                        Comparator.comparingLong(
                                S3Object::size);

                break;

            case LAST_MODIFIED:

                comparator =
                        Comparator.comparing(
                                S3Object::lastModified,
                                Comparator.nullsFirst(
                                        Instant::compareTo));

                break;

            case NAME:
            default:

                comparator =
                        Comparator.comparing(
                                S3Object::key,
                                collator);

                break;
        }

        if (!ascending) {
            comparator =
                    comparator.reversed();
        }

        /*
         * Deterministic ordering.
         *
         * Eğer iki kayıt primary sort alanında
         * eşitse key'e göre ikinci bir sıralama
         * yapıyoruz.
         */
        Comparator<S3Object> keyComparator =
                Comparator.comparing(
                        S3Object::key,
                        collator);

        if (!ascending) {
            keyComparator =
                    keyComparator.reversed();
        }

        return comparator.thenComparing(
                keyComparator);
    }

    public static FileTableSortSpec defaultSpec() {

        return new FileTableSortSpec(
                Column.NAME,
                true);
    }
}