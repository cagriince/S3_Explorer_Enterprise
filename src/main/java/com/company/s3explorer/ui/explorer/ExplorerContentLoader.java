package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.*;

import java.text.CollationKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

    private final Map<String, CollationKey>
            collationKeyCache;

    /**
     * Identical concurrent requests share the same future.
     */
    private final Map<String, CompletableFuture<LimitedFolderContent>>
            inFlightLoads =
            new ConcurrentHashMap<>();

    /**
     * Completed, non-limited content.
     *
     * We intentionally keep the cache scoped to one bucket/prefix.
     */
    private volatile LimitedFolderContent cachedContent;

    private volatile FolderContentSnapshot cachedSnapshot;

    private volatile String cachedBucket;

    private volatile String cachedPrefix;

    private volatile boolean cachedFlatBrowsing;

    /**
     * Concurrent requests for the same folder share one full S3 discovery.
     */
    private final Map<String, CompletableFuture<FolderContentSnapshot>>
            inFlightSnapshots = new ConcurrentHashMap<>();

    long primaryEqualComparisons = -1;
    long secondaryNonZeroComparisons = -1;

    public ExplorerContentLoader(
            Supplier<S3ExplorerService> serviceSupplier) {

        this.serviceSupplier =
                Objects.requireNonNull(
                        serviceSupplier,
                        "serviceSupplier");

        this.collationKeyCache =
                new ConcurrentHashMap<>();
    }

    public void clearCollationKeyCache() {
        collationKeyCache.clear();
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
                                    collationKeyCache,
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
                bucket
                        + "\u0000"
                        + prefix
                        + "\u0000"
                        + flatBrowsing;

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

                                    cachedSnapshot = snapshot;
                                    cachedBucket = bucket;
                                    cachedPrefix = prefix;
                                    cachedFlatBrowsing = flatBrowsing;

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

    public void invalidate(
            String bucket,
            String prefix) {

        if (!Objects.equals(cachedBucket, bucket)
                || !Objects.equals(cachedPrefix, prefix)) {
            return;
        }

        cachedSnapshot = null;
        cachedBucket = null;
        cachedPrefix = null;
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

        long start = System.nanoTime();

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

        List<software.amazon.awssdk.services.s3.model.S3Object>
                matchingFiles = new java.util.ArrayList<>();

        if (filter.includesFiles()) {
            for (software.amazon.awssdk.services.s3.model.S3Object object
                    : snapshot.files()) {

                String displayName = getFilterDisplayName(
                        object.key(),
                        prefix,
                        false,
                        flatBrowsing);

                if (filter.matchesText(displayName)) {
                    matchingFiles.add(object);
                }
            }
        }

        long matchingFileCount = matchingFiles.size();

        java.util.Comparator<
                software.amazon.awssdk.services.s3.model.S3Object>
                comparator = sortSpec.createFileComparator(
                collationKeyCache);

        matchingFiles.sort(comparator);

        boolean fileLimitReached =
                matchingFiles.size() > fileLimit;

        if (fileLimitReached) {
            matchingFiles.subList(
                    fileLimit,
                    matchingFiles.size()).clear();
        }

        LimitedFolderContent result =
                new LimitedFolderContent(
                        matchingFolders,
                        matchingFiles,
                        fileLimitReached,
                        matchingFileCount);

        long elapsedMs =
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start);

        System.err.printf(
                "[SNAPSHOT VIEW] elapsed=%d ms, "
                        + "snapshotFiles=%d, displayedFiles=%d, "
                        + "snapshotFolders=%d, displayedFolders=%d, "
                        + "filterType=%s, filterText=%s, "
                        + "sortColumn=%s, ascending=%s%n",
                elapsedMs,
                snapshot.files().size(),
                result.files().size(),
                snapshot.folders().size(),
                result.folders().size(),
                filter.itemType(),
                filter.text(),
                sortSpec.getColumn(),
                sortSpec.isAscending());

        return result;
    }

    private LimitedFolderContent createFilteredDisplayContent(
            LimitedFolderContent cached,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FileTableFilter filter,
            String prefix,
            boolean flatBrowsing) {

        long methodStart = System.nanoTime();

        List<String> matchingFolders = new java.util.ArrayList<>();

        if (filter.includesFolders()) {
            for (String folderKey : cached.folders()) {
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

        long matchingFileCount = 0;

        List<software.amazon.awssdk.services.s3.model.S3Object>
                matchingFiles = new java.util.ArrayList<>();

        long scanNanos = 0;

        if (filter.includesFiles() && !filter.hasTextFilter()) {

            matchingFileCount = cached.files().size();
            matchingFiles.addAll(cached.files());

        } else if (filter.includesFiles()) {

            for (software.amazon.awssdk.services.s3.model.S3Object object
                    : cached.files()) {

                long scanStart = System.nanoTime();

                String displayName = getFilterDisplayName(
                        object.key(),
                        prefix,
                        false,
                        flatBrowsing);

                boolean matches = filter.matchesText(displayName);

                scanNanos += System.nanoTime() - scanStart;

                if (!matches) {
                    continue;
                }

                matchingFileCount++;
                matchingFiles.add(object);
            }
        }

        long comparatorBuildStart = System.nanoTime();

        java.util.Comparator<
                software.amazon.awssdk.services.s3.model.S3Object>
                actualComparator =
                sortSpec.createFileComparator(collationKeyCache);

        long comparatorBuildNanos =
                System.nanoTime() - comparatorBuildStart;

        int cacheSizeBeforeSort = collationKeyCache.size();

        long sortStart = System.nanoTime();

        matchingFiles.sort(actualComparator);

        long sortNanos = System.nanoTime() - sortStart;

        long primaryComparisons = -1;
        long secondaryComparisons = -1;
        long collationKeyCacheMisses = -1;
        long collationKeyCreationNanos = -1;

        if (actualComparator
                instanceof FileTableSortSpec.ComparisonMetrics) {

            FileTableSortSpec.ComparisonMetrics metrics =
                    (FileTableSortSpec.ComparisonMetrics) actualComparator;

            primaryComparisons =
                    metrics.getPrimaryComparisonCount();

            secondaryComparisons =
                    metrics.getSecondaryComparisonCount();

            collationKeyCacheMisses =
                    metrics.getCollationKeyCacheMissCount();

            collationKeyCreationNanos =
                    metrics.getCollationKeyCreationNanos();

            primaryEqualComparisons =
                    metrics.getPrimaryEqualComparisonCount();

            secondaryNonZeroComparisons =
                    metrics.getSecondaryNonZeroComparisonCount();
        }

        int cacheSizeAfterSort = collationKeyCache.size();

        long trimStart = System.nanoTime();

        if (matchingFiles.size() > fileLimit) {
            matchingFiles.subList(
                    fileLimit,
                    matchingFiles.size()).clear();
        }

        long trimNanos = System.nanoTime() - trimStart;

        boolean fileLimitReached =
                matchingFileCount > matchingFiles.size();

        long resultStart = System.nanoTime();

        LimitedFolderContent result =
                new LimitedFolderContent(
                        matchingFolders,
                        matchingFiles,
                        fileLimitReached,
                        matchingFileCount);

        long resultCreationNanos =
                System.nanoTime() - resultStart;

        long totalNanos =
                System.nanoTime() - methodStart;

        System.err.printf(
                "[FILTER PROFILE] column=%s, ascending=%s, "
                        + "scan=%d ms, comparatorBuild=%d ms, "
                        + "sort=%d ms, primaryComparisons=%d, "
                        + "secondaryComparisons=%d, "
                        + "collationKeyCacheBefore=%d, "
                        + "collationKeyCacheAfter=%d, "
                        + "collationKeyCacheMisses=%d, "
                        + "collationKeyCreation=%d ms, "
                        + "trim=%d ms, resultCreation=%d ms, "
                        + "total=%d ms, matchingFiles=%d, "
                        + "displayedFiles=%d%n"
                        + "primaryEqualComparisons=%d, "
                        + "secondaryNonZeroComparisons=%d, ",
                sortSpec.getColumn(),
                sortSpec.isAscending(),
                TimeUnit.NANOSECONDS.toMillis(scanNanos),
                TimeUnit.NANOSECONDS.toMillis(comparatorBuildNanos),
                TimeUnit.NANOSECONDS.toMillis(sortNanos),
                primaryComparisons,
                secondaryComparisons,
                cacheSizeBeforeSort,
                cacheSizeAfterSort,
                collationKeyCacheMisses,
                TimeUnit.NANOSECONDS.toMillis(
                        collationKeyCreationNanos < 0
                                ? 0
                                : collationKeyCreationNanos),
                TimeUnit.NANOSECONDS.toMillis(trimNanos),
                TimeUnit.NANOSECONDS.toMillis(resultCreationNanos),
                TimeUnit.NANOSECONDS.toMillis(totalNanos),
                matchingFileCount,
                matchingFiles.size(),
                primaryEqualComparisons,
                secondaryNonZeroComparisons);

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

    private String createLoadKey(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        return bucket
                + "\u0000"
                + prefix
                + "\u0000"
                + fileLimit
                + "\u0000"
                + sortSpec.getColumn()
                + "\u0000"
                + sortSpec.isAscending();
    }
}