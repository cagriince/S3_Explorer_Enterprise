package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Loads folder content for ExplorerPanel.
 *
 * <p>
 * The loader deliberately has no Swing dependency.
 * A single loadFolder(...) operation obtains both direct folders
 * and files through LimitedFolderContent.
 * </p>
 */
public final class ExplorerContentLoader {

    private final Supplier<S3ExplorerService> serviceSupplier;

    /**
     * Identical concurrent requests share the same future.
     */
    private final Map<String, CompletableFuture<LimitedFolderContent>>
            inFlightLoads =
            new ConcurrentHashMap<>();

    private volatile FolderContentSnapshot cachedSnapshot;

    private volatile String cachedBucket;

    private volatile String cachedPrefix;

    private volatile boolean cachedFlatBrowsing;

    /**
     * Concurrent requests for the same folder share one full S3 discovery.
     */
    private final Map<String, CompletableFuture<FolderContentSnapshot>>
            inFlightSnapshots = new ConcurrentHashMap<>();

    /**
     * Changes whenever the cached snapshot is invalidated.
     * Prevents an older in-flight scan from repopulating the cache.
     */
    private final AtomicLong snapshotGeneration = new AtomicLong();

    public ExplorerContentLoader(
            Supplier<S3ExplorerService> serviceSupplier) {

        this.serviceSupplier =
                Objects.requireNonNull(
                        serviceSupplier,
                        "serviceSupplier");
    }

    /**
     * Loads without a progress listener.
     */
    public CompletableFuture<LimitedFolderContent> loadFolders(
            ExecutorService executor,
            String bucket,
            String prefix,
            FolderDiscoveryListener discoveryListener) {

        Objects.requireNonNull(
                executor,
                "executor");

        Objects.requireNonNull(
                bucket,
                "bucket");

        Objects.requireNonNull(
                prefix,
                "prefix");

        String loadKey =
                bucket
                        + "\u0000"
                        + prefix
                        + "\u0000FOLDERS_ONLY";

        CompletableFuture<LimitedFolderContent> existing =
                inFlightLoads.get(loadKey);

        if (existing != null) {
            return existing;
        }

        CompletableFuture<LimitedFolderContent> created =
                CompletableFuture.supplyAsync(
                        () -> {

                            S3ExplorerService service =
                                    Objects.requireNonNull(
                                            serviceSupplier.get(),
                                            "serviceSupplier returned null");

                            return service.listFolderWithLimit(
                                    bucket,
                                    prefix,
                                    1,
                                    FileTableSortSpec.defaultSpec(),
                                    discoveryListener,
                                    FolderContentMode.FOLDERS_ONLY);
                        },
                        executor);

        CompletableFuture<LimitedFolderContent> actual =
                inFlightLoads.putIfAbsent(
                        loadKey,
                        created);

        if (actual != null) {
            return actual;
        }

        created.whenComplete(
                (result, throwable) ->
                        inFlightLoads.remove(
                                loadKey,
                                created));

        return created;
    }

    public CompletableFuture<LimitedFolderContent> loadFolder(
            ExecutorService executor,
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing) {

        return loadFolder(
                executor,
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                discoveryListener,
                flatBrowsing,
                FileTableFilter.noFilter());
    }

    /**
     * Loads both folders and files.
     *
     * <p>
     * The ExecutorService is supplied by ExplorerPanel because
     * that pool can be resized at runtime.
     * </p>
     */
    public CompletableFuture<LimitedFolderContent> loadFolder(
            ExecutorService executor,
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing,
            FileTableFilter filter) {

        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(sortSpec, "sortSpec");

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        FileTableFilter effectiveFilter =
                filter == null
                        ? FileTableFilter.noFilter()
                        : filter;

        FolderContentSnapshot cached =
                getCachedSnapshot(bucket, prefix, flatBrowsing);

        if (cached != null) {
            return CompletableFuture.supplyAsync(
                    () -> createDisplayContent(
                            cached,
                            fileLimit,
                            sortSpec,
                            effectiveFilter,
                            prefix,
                            flatBrowsing),
                    executor);
        }

        String snapshotKey =
                bucket + "\u0000" + prefix + "\u0000" + flatBrowsing;

        /*
         * Capture the generation before starting or joining a scan.
         * A scan started before invalidation must not repopulate the cache.
         */
        long generationAtStart = snapshotGeneration.get();

        CompletableFuture<FolderContentSnapshot> snapshotFuture =
                inFlightSnapshots.computeIfAbsent(
                        snapshotKey,
                        key -> CompletableFuture.supplyAsync(
                                () -> {
                                    S3ExplorerService service =
                                            Objects.requireNonNull(
                                                    serviceSupplier.get(),
                                                    "serviceSupplier returned null");

                                    FolderContentSnapshot snapshot =
                                            service.listFolderSnapshot(
                                                    bucket,
                                                    prefix,
                                                    discoveryListener,
                                                    flatBrowsing);

                                    /*
                                     * Publish only if no invalidation happened
                                     * while this scan was running.
                                     */
                                    if (snapshotGeneration.get()
                                            == generationAtStart) {

                                        synchronized (this) {
                                            if (snapshotGeneration.get()
                                                    == generationAtStart) {
                                                cachedSnapshot = snapshot;
                                                cachedBucket = bucket;
                                                cachedPrefix = prefix;
                                                cachedFlatBrowsing = flatBrowsing;
                                            }
                                        }
                                    } else {
                                        System.err.printf(
                                                "[SNAPSHOT CACHE] discarded stale scan "
                                                        + "bucket=%s prefix=%s%n",
                                                bucket,
                                                prefix);
                                    }

                                    return snapshot;
                                },
                                executor));

        snapshotFuture.whenComplete(
                (snapshot, throwable) ->
                        inFlightSnapshots.remove(
                                snapshotKey,
                                snapshotFuture));

        return snapshotFuture.thenApplyAsync(
                snapshot -> createDisplayContent(
                        snapshot,
                        fileLimit,
                        sortSpec,
                        effectiveFilter,
                        prefix,
                        flatBrowsing),
                executor);
    }

    /**
     * Returns a complete cached result.
     *
     * <p>
     * A result that stopped because of fileLimit cannot be treated
     * as a complete cache.
     * </p>
     */
    public FolderContentSnapshot getCachedSnapshot(
            String bucket,
            String prefix,
            boolean flatBrowsing) {

        FolderContentSnapshot snapshot = cachedSnapshot;

        if (snapshot == null
                || !Objects.equals(cachedBucket, bucket)
                || !Objects.equals(cachedPrefix, prefix)
                || cachedFlatBrowsing != flatBrowsing) {
            return null;
        }

        return snapshot;
    }

    public boolean isDiscoveryInProgress(
            String bucket,
            String prefix,
            boolean flatBrowsing) {

        if (bucket == null || prefix == null) {
            return false;
        }

        String snapshotKey =
                bucket + "\u0000" + prefix + "\u0000" + flatBrowsing;

        return inFlightSnapshots.containsKey(snapshotKey);
    }

    public void invalidate(
            String bucket,
            String prefix) {

        if (bucket == null || prefix == null) {
            return;
        }

        String keyPrefix =
                bucket + "\u0000" + prefix + "\u0000";

        boolean cachedMatches =
                Objects.equals(cachedBucket, bucket)
                        && Objects.equals(cachedPrefix, prefix);

        boolean inFlightMatches =
                inFlightSnapshots.keySet().stream()
                        .anyMatch(key -> key.startsWith(keyPrefix));

        if (!cachedMatches && !inFlightMatches) {
            return;
        }

        synchronized (this) {
            /*
             * Increment first so a running scan cannot publish stale data.
             */
            snapshotGeneration.incrementAndGet();

            if (Objects.equals(cachedBucket, bucket)
                    && Objects.equals(cachedPrefix, prefix)) {

                cachedSnapshot = null;
                cachedBucket = null;
                cachedPrefix = null;
            }
        }

        /*
         * Remove matching futures from the map. Existing callers may still
         * receive their result, but a subsequent load can start a fresh scan.
         */
        inFlightSnapshots.keySet().removeIf(
                key -> key.startsWith(keyPrefix));

        System.err.printf(
                "[SNAPSHOT CACHE] invalidated bucket=%s prefix=%s%n",
                bucket,
                prefix);
    }

    /**
     * Creates a display-limited result from a complete
     * cached result without making another S3 request.
     */

    private LimitedFolderContent createDisplayContent(
            FolderContentSnapshot snapshot,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FileTableFilter filter,
            String prefix,
            boolean flatBrowsing) {

        long totalStart = System.nanoTime();

        // Filter folders.
        long folderScanStart = System.nanoTime();

        List<String> matchingFolders = new java.util.ArrayList<>();

        if (filter.includesFolders()) {
            for (String folderKey : snapshot.folders()) {
                String displayName = getFilterDisplayName(
                        folderKey,
                        prefix,
                        true,
                        flatBrowsing);

                if (filter.matchesText(displayName)) {
                    matchingFolders.add(folderKey);
                }
            }
        }

        long folderScanNanos =
                System.nanoTime() - folderScanStart;

        /*
         * Keep only the first fileLimit matching files until the limit
         * is exceeded. If it is exceeded, switch to a bounded priority
         * queue and retain only the best candidates according to the
         * requested sort order.
         *
         * The source index is used as a tie-breaker to preserve the
         * stable ordering of files that compare as equal.
         */
        long fileScanStart = System.nanoTime();

        List<IndexedFile> selectedFiles =
                new java.util.ArrayList<>(
                        Math.min(snapshot.files().size(), fileLimit));

        java.util.PriorityQueue<IndexedFile> bestFiles = null;

        java.util.Comparator<IndexedFile> stableComparator = null;

        long matchingFileCount = 0;

        long comparatorNanos = 0;
        long selectionAndSortNanos = 0;

        if (filter.includesFiles()) {
            List<software.amazon.awssdk.services.s3.model.S3Object>
                    sourceFiles = snapshot.files();

            for (int sourceIndex = 0;
                 sourceIndex < sourceFiles.size();
                 sourceIndex++) {

                software.amazon.awssdk.services.s3.model.S3Object object =
                        sourceFiles.get(sourceIndex);

                String displayName = getFilterDisplayName(
                        object.key(),
                        prefix,
                        false,
                        flatBrowsing);

                if (!filter.matchesText(displayName)) {
                    continue;
                }

                matchingFileCount++;

                IndexedFile candidate =
                        new IndexedFile(sourceIndex, object);

                // Until the limit is exceeded, preserve the original order.
                if (bestFiles == null
                        && selectedFiles.size() < fileLimit) {

                    selectedFiles.add(candidate);
                    continue;
                }

                /*
                 * The first matching file beyond the limit triggers
                 * bounded selection. The queue head must be the worst
                 * candidate currently retained.
                 */
                if (bestFiles == null) {
                    long comparatorStart = System.nanoTime();

                    java.util.Comparator<
                            software.amazon.awssdk.services.s3.model.S3Object>
                            fileComparator =
                            sortSpec.createFileComparator();

                    stableComparator = (left, right) -> {
                        int comparison = fileComparator.compare(
                                left.object(),
                                right.object());

                        if (comparison != 0) {
                            return comparison;
                        }

                        return Integer.compare(
                                left.sourceIndex(),
                                right.sourceIndex());
                    };

                    comparatorNanos +=
                            System.nanoTime() - comparatorStart;

                    long selectionStart = System.nanoTime();

                    bestFiles = new java.util.PriorityQueue<>(
                            fileLimit,
                            stableComparator.reversed());

                    bestFiles.addAll(selectedFiles);
                    selectedFiles.clear();

                    selectionAndSortNanos +=
                            System.nanoTime() - selectionStart;
                }

                long selectionStart = System.nanoTime();

                IndexedFile worstCandidate = bestFiles.peek();

                if (stableComparator.compare(
                        candidate,
                        worstCandidate) < 0) {

                    bestFiles.poll();
                    bestFiles.offer(candidate);
                }

                selectionAndSortNanos +=
                        System.nanoTime() - selectionStart;
            }
        }

        long fileScanNanos =
                System.nanoTime() - fileScanStart;

        boolean fileLimitReached =
                matchingFileCount > fileLimit;

        /*
         * If the limit was exceeded, sort only the retained candidates
         * into their final display order. Otherwise, preserve the source
         * order as before; the JTable sorter handles the visible order.
         */
        long finalSortStart = System.nanoTime();

        List<software.amazon.awssdk.services.s3.model.S3Object>
                matchingFiles =
                new java.util.ArrayList<>(
                        bestFiles == null
                                ? selectedFiles.size()
                                : bestFiles.size());

        if (bestFiles == null) {
            for (IndexedFile indexedFile : selectedFiles) {
                matchingFiles.add(indexedFile.object());
            }
        } else {
            List<IndexedFile> sortedFiles =
                    new java.util.ArrayList<>(bestFiles);

            sortedFiles.sort(stableComparator);

            for (IndexedFile indexedFile : sortedFiles) {
                matchingFiles.add(indexedFile.object());
            }
        }

        selectionAndSortNanos +=
                System.nanoTime() - finalSortStart;

        long resultStart = System.nanoTime();

        LimitedFolderContent result =
                new LimitedFolderContent(
                        matchingFolders,
                        matchingFiles,
                        fileLimitReached,
                        matchingFileCount);

        long resultNanos =
                System.nanoTime() - resultStart;

        long totalNanos =
                System.nanoTime() - totalStart;

        System.err.printf(
                "[SNAPSHOT PROFILE] "
                        + "folderScan=%d ms, "
                        + "fileScan=%d ms, "
                        + "comparatorBuild=%d ms, "
                        + "sort=%d ms, "
                        + "sortSkipped=%s, "
                        + "trim=0 ms, "
                        + "resultCreation=%d ms, "
                        + "total=%d ms, "
                        + "snapshotFiles=%d, "
                        + "matchingFiles=%d, "
                        + "displayedFiles=%d, "
                        + "snapshotFolders=%d, "
                        + "displayedFolders=%d, "
                        + "filterType=%s, "
                        + "filterText=%s, "
                        + "sortColumn=%s, "
                        + "ascending=%s%n",
                TimeUnit.NANOSECONDS.toMillis(folderScanNanos),
                TimeUnit.NANOSECONDS.toMillis(fileScanNanos),
                TimeUnit.NANOSECONDS.toMillis(comparatorNanos),
                TimeUnit.NANOSECONDS.toMillis(selectionAndSortNanos),
                !fileLimitReached,
                TimeUnit.NANOSECONDS.toMillis(resultNanos),
                TimeUnit.NANOSECONDS.toMillis(totalNanos),
                snapshot.files().size(),
                matchingFileCount,
                result.files().size(),
                snapshot.folders().size(),
                result.folders().size(),
                filter.itemType(),
                filter.text(),
                sortSpec.getColumn(),
                sortSpec.isAscending());

        return result;
    }

    private String getFilterDisplayName(
            String key,
            String prefix,
            boolean folder,
            boolean flatBrowsing) {

        String effectivePrefix =
                prefix == null ? "" : prefix;

        String displayName;

        if (flatBrowsing) {
            displayName = key.startsWith(effectivePrefix)
                    ? key.substring(effectivePrefix.length())
                    : key;
        } else {
            String normalizedKey = key;

            if (folder && normalizedKey.endsWith("/")) {
                normalizedKey = normalizedKey.substring(
                        0,
                        normalizedKey.length() - 1);
            }

            int slash = normalizedKey.lastIndexOf('/');

            displayName = slash >= 0
                    ? normalizedKey.substring(slash + 1)
                    : normalizedKey;
        }

        if (folder && displayName.endsWith("/")) {
            displayName = displayName.substring(
                    0,
                    displayName.length() - 1);
        }

        return displayName;
    }

    private record IndexedFile(
            int sourceIndex,
            software.amazon.awssdk.services.s3.model.S3Object object) {
    }
}