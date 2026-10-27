
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


    public Comparator<S3Object> createFileComparator() {

        Collator turkishCollator =
                Collator.getInstance(new Locale("tr", "TR"));

        turkishCollator.setStrength(Collator.PRIMARY);

        long[] cacheMissCount = {0};
        long[] keyCreationNanos = {0};

        /*
         * Reuse character-level collation keys across comparisons.
         * This avoids creating a full CollationKey for every
         * distinct filename when comparing secondary sort values.
         */
        Map<Character, CollationKey> characterKeyCache =
                new HashMap<>();

        Comparator<S3Object> primaryComparator;

        switch (column) {
            case SIZE:
                primaryComparator =
                        Comparator.comparingLong(S3Object::size);
                break;

            case LAST_MODIFIED:
                primaryComparator =
                        Comparator.comparing(
                                S3Object::lastModified,
                                Comparator.nullsFirst(
                                        Instant::compareTo));
                break;

            case NAME:
            default:
                primaryComparator =
                        (left, right) ->
                                S3Util.naturalTurkishCompare(
                                        left.key(),
                                        right.key(),
                                        turkishCollator,
                                        characterKeyCache);
                break;
        }

        Comparator<S3Object> secondaryComparator = null;

        if (column != Column.NAME) {
            secondaryComparator = (left, right) ->
                    S3Util.naturalTurkishCompare(
                            left.key(),
                            right.key(),
                            turkishCollator,
                            characterKeyCache);
        }

        return new MeasuredComparator(
                primaryComparator,
                secondaryComparator,
                ascending,
                cacheMissCount,
                keyCreationNanos);
    }

    private static final class MeasuredComparator
            implements Comparator<S3Object> {

        private final Comparator<S3Object> primaryComparator;
        private final Comparator<S3Object> secondaryComparator;
        private final boolean ascending;

        private final long[] cacheMissCount;
        private final long[] keyCreationNanos;

        private long primaryComparisonCount;
        private long primaryEqualComparisonCount;
        private long secondaryComparisonCount;
        private long secondaryNonZeroComparisonCount;

        private MeasuredComparator(
                Comparator<S3Object> primaryComparator,
                Comparator<S3Object> secondaryComparator,
                boolean ascending,
                long[] cacheMissCount,
                long[] keyCreationNanos) {

            this.primaryComparator = primaryComparator;
            this.secondaryComparator = secondaryComparator;
            this.ascending = ascending;
            this.cacheMissCount = cacheMissCount;
            this.keyCreationNanos = keyCreationNanos;
        }

        @Override
        public int compare(
                S3Object left,
                S3Object right) {

            primaryComparisonCount++;

            int result =
                    primaryComparator.compare(left, right);

            if (result != 0) {
                return ascending
                        ? Integer.signum(result)
                        : -Integer.signum(result);
            }

            if (secondaryComparator == null) {
                return 0;
            }

            primaryEqualComparisonCount++;
            secondaryComparisonCount++;

            result = secondaryComparator.compare(left, right);

            if (result != 0) {
                secondaryNonZeroComparisonCount++;
            }

            return ascending
                    ? Integer.signum(result)
                    : -Integer.signum(result);
        }
    }

    public static FileTableSortSpec defaultSpec() {
        return new FileTableSortSpec(Column.NAME, true);
    }
}