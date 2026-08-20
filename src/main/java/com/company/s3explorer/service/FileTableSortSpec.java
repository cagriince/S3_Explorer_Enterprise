package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.text.CollationKey;
import java.text.Collator;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    public Comparator<S3Object> createFileComparator() {
        return createFileComparator(new HashMap<>());
    }

    public Comparator<S3Object> createFileComparator(Map<String, CollationKey> collationKeyCache) {

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
                        (left, right) -> {

                            CollationKey leftKey =
                                    collationKeyCache
                                            .computeIfAbsent(
                                                    left.key(),
                                                    collator::getCollationKey);

                            CollationKey rightKey =
                                    collationKeyCache
                                            .computeIfAbsent(
                                                    right.key(),
                                                    collator::getCollationKey);

                            return leftKey.compareTo(
                                    rightKey);
                        };

                break;
        }

        if (!ascending) {

            comparator =
                    comparator.reversed();
        }

        /*
         * NAME zaten key üzerinden sıralandığı için
         * ikinci key comparator'a gerek yok.
         */
        if (column != Column.NAME) {

            Comparator<S3Object> keyComparator =
                    Comparator.comparing(
                            S3Object::key,
                            collator);

            if (!ascending) {
                keyComparator =
                        keyComparator.reversed();
            }

            comparator =
                    comparator.thenComparing(
                            keyComparator);
        }

        return comparator;
    }
    
    public static FileTableSortSpec defaultSpec() {

        return new FileTableSortSpec(
                Column.NAME,
                true);
    }
}