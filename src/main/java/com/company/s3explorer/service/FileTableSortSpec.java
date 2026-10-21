
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

    /**
     * Exposes comparison counters for performance diagnostics.
     */
    public interface ComparisonMetrics {
        long getPrimaryComparisonCount();

        long getSecondaryComparisonCount();
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
                Collator.getInstance(new Locale("tr", "TR"));

        turkishCollator.setStrength(Collator.PRIMARY);

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
                Map<Character, CollationKey> characterKeyCache =
                        new HashMap<>();

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
            secondaryComparator = (left, right) -> {
                CollationKey leftKey =
                        collationKeyCache.computeIfAbsent(
                                left.key(),
                                turkishCollator::getCollationKey);

                CollationKey rightKey =
                        collationKeyCache.computeIfAbsent(
                                right.key(),
                                turkishCollator::getCollationKey);

                return leftKey.compareTo(rightKey);
            };
        }

        return new MeasuredComparator(
                primaryComparator,
                secondaryComparator,
                ascending);
    }

    private static final class MeasuredComparator
            implements Comparator<S3Object>, ComparisonMetrics {

        private final Comparator<S3Object> primaryComparator;
        private final Comparator<S3Object> secondaryComparator;
        private final boolean ascending;

        private long primaryComparisonCount;
        private long secondaryComparisonCount;

        private MeasuredComparator(
                Comparator<S3Object> primaryComparator,
                Comparator<S3Object> secondaryComparator,
                boolean ascending) {

            this.primaryComparator = primaryComparator;
            this.secondaryComparator = secondaryComparator;
            this.ascending = ascending;
        }

        @Override
        public int compare(
                S3Object left,
                S3Object right) {

            primaryComparisonCount++;

            int result =
                    primaryComparator.compare(left, right);

            if (!ascending) {
                result = -Integer.signum(result);
            }

            if (result != 0 || secondaryComparator == null) {
                return result;
            }

            secondaryComparisonCount++;

            result = secondaryComparator.compare(left, right);

            if (!ascending) {
                result = -Integer.signum(result);
            }

            return result;
        }

        @Override
        public long getPrimaryComparisonCount() {
            return primaryComparisonCount;
        }

        @Override
        public long getSecondaryComparisonCount() {
            return secondaryComparisonCount;
        }
    }

    public static FileTableSortSpec defaultSpec() {
        return new FileTableSortSpec(Column.NAME, true);
    }
}