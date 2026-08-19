package com.company.s3explorer.ui.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps at most {@code limit} file items in sorted order.
 *
 * Folders are intentionally NOT handled by this class.
 * The collection is only responsible for files.
 *
 * New files are inserted into their correct position and,
 * when the limit is exceeded, the worst file is removed.
 */
public class BoundedSortedFileCollection {

    private final int limit;
    private final Comparator<S3FileItem> comparator;
    private final List<S3FileItem> items;

    public BoundedSortedFileCollection(
            int limit,
            Comparator<S3FileItem> comparator) {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be greater than zero");
        }

        this.limit = limit;
        this.comparator = comparator;
        this.items = new ArrayList<>(Math.min(limit, 1024));
    }

    /**
     * Adds one file while preserving the sorted order.
     *
     * The list never grows beyond the configured limit.
     */
    public void add(S3FileItem item) {

        if (item == null || !item.isFile()) {
            return;
        }

        /*
         * Find insertion point using binary search.
         */
        int index =
                Collections.binarySearch(
                        items,
                        item,
                        comparator);

        if (index < 0) {
            index = -index - 1;
        } else {
            /*
             * If equal elements exist, insert after them.
             */
            while (index < items.size()
                    && comparator.compare(
                    items.get(index),
                    item) == 0) {

                index++;
            }
        }

        /*
         * If collection is already full and the new item
         * is worse than the current worst item, there is
         * nothing to do.
         *
         * Since the list is sorted ascending according
         * to comparator, the worst item is the last item.
         */
        if (items.size() >= limit
                && index >= items.size()) {

            return;
        }

        items.add(index, item);

        /*
         * Remove the worst item if we exceeded the limit.
         */
        if (items.size() > limit) {
            items.remove(items.size() - 1);
        }
    }

    public void addAll(
            Iterable<S3FileItem> source) {

        if (source == null) {
            return;
        }

        for (S3FileItem item : source) {
            add(item);
        }
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<S3FileItem> toList() {
        return new ArrayList<>(items);
    }

    public void clear() {
        items.clear();
    }

    public int getLimit() {
        return limit;
    }
}