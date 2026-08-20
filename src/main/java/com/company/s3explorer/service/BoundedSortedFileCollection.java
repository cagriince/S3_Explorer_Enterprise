package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.*;

public class BoundedSortedFileCollection {

    private final int limit;

    private final Comparator<S3Object> comparator;

    private final List<S3Object> items;

    public BoundedSortedFileCollection(
            int limit,
            Comparator<S3Object> comparator) {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be greater than zero");
        }

        this.limit = limit;
        this.comparator = comparator;

        this.items =
                new ArrayList<>(
                        Math.min(limit, 1024));
    }

    public void add(S3Object item) {

        if (item == null) {
            return;
        }

        int index =
                Collections.binarySearch(
                        items,
                        item,
                        comparator);

        if (index < 0) {
            index = -index - 1;
        }
        else {

            while (index < items.size()
                    && comparator.compare(
                    items.get(index),
                    item) == 0) {

                index++;
            }
        }

        /*
         * Liste zaten dolu ve yeni item mevcut
         * listenin en kötüsünden daha kötü ise
         * hiç eklemiyoruz.
         */
        if (items.size() >= limit
                && index >= items.size()) {

            return;
        }

        items.add(index, item);

        /*
         * Limit aşıldıysa en kötü kaydı at.
         */
        if (items.size() > limit) {

            items.remove(
                    items.size() - 1);
        }
    }

    public void addAll(
            Iterable<S3Object> source) {

        if (source == null) {
            return;
        }

        /*
         * Eğer kaynak zaten Collection ise,
         * tek tek insertion yapmak yerine
         * topluca sort etmek çok daha hızlıdır.
         */
        if (source instanceof java.util.Collection<?> collection) {

            @SuppressWarnings("unchecked")
            Collection<S3Object> objects =
                    (Collection<S3Object>) collection;

            items.clear();

            items.addAll(objects);

            items.sort(comparator);

            if (items.size() > limit) {

                items.subList(
                        limit,
                        items.size()).clear();
            }

            return;
        }

        /*
         * Streaming kaynaklar için mevcut
         * bounded insertion algoritması.
         */
        for (S3Object item : source) {
            add(item);
        }
    }
    
    public int size() {
        return items.size();
    }

    public List<S3Object> toList() {
        return new ArrayList<>(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getLimit() {
        return limit;
    }

    public void clear() {
        items.clear();
    }
}