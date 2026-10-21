
package com.company.s3explorer.service;

import com.company.s3explorer.util.S3Util;
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

    public Comparator<S3Object> createFileComparator(
            Map<String, CollationKey> collationKeyCache) {

        Collator turkishCollator =
                Collator.getInstance(
                        new Locale("tr", "TR"));

        turkishCollator.setStrength(
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

                /*
                 * Character collation keys are local to this
                 * comparator, so repeated comparisons reuse them.
                 * This map is accessed by the sorting thread.
                 */
                Map<Character, CollationKey> characterKeyCache =
                        new HashMap<>();

                comparator =
                        (left, right) ->
                                S3Util.naturalTurkishCompare(
                                        left.key(),
                                        right.key(),
                                        turkishCollator,
                                        characterKeyCache);

                break;
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        /*
         * NAME already compares keys through the natural
         * Turkish comparator, so no additional key comparison
         * is needed.
         */
        if (column != Column.NAME) {

            Comparator<S3Object> keyComparator =
                    Comparator.comparing(
                            S3Object::key,
                            turkishCollator);

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