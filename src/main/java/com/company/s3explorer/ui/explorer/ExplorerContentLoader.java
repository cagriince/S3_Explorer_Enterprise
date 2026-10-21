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

    private volatile String cachedBucket;

    private volatile String cachedPrefix;

    private volatile boolean cachedFlatBrowsing;

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
    public CompletableFuture<LimitedFolderContent> loadFolder(
            ExecutorService executor,
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        return loadFolder(
                executor,
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                null);
    }

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
            FolderDiscoveryListener discoveryListener) {

        return loadFolder(
                executor,
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                discoveryListener,
                false);
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

        boolean unfiltered =
                !effectiveFilter.hasTextFilter()
                        && effectiveFilter.itemType()
                        == ItemTypeFilter.ALL;

        /*
         * Tam liste önbellekteyse, hem filtresiz hem de
         * filtreli görüntüleme S3 isteği olmadan hazırlanabilir.
         */
        LimitedFolderContent cached =
                getCachedContent(bucket, prefix, flatBrowsing);

        if (cached != null) {
            if (unfiltered) {
                return CompletableFuture.supplyAsync(
                        () -> createDisplayContent(
                                cached,
                                fileLimit,
                                sortSpec),
                        executor);
            }

            return CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();

                LimitedFolderContent result =
                        createFilteredDisplayContent(
                                cached,
                                fileLimit,
                                sortSpec,
                                effectiveFilter,
                                prefix,
                                flatBrowsing);

                long elapsedMs =
                        TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - start);

                System.err.printf(
                        "[FILTER TIMING] cached filtering: %d ms, "
                                + "sourceFiles=%d, resultFiles=%d, "
                                + "sourceFolders=%d, resultFolders=%d%n",
                        elapsedMs,
                        cached.files().size(),
                        result.files().size(),
                        cached.folders().size(),
                        result.folders().size());

                return result;
            }, executor);
        }

        /*
         * Tam liste önbellekte değilse S3 üzerinden filtreli
         * listeleme yapılır. Filtre, eşzamanlı yükleme anahtarına
         * dahil edilir; farklı filtreler yanlışlıkla aynı sonucu paylaşmaz.
         */
        String loadKey =
                createLoadKey(
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec)
                        + "\u0000" + flatBrowsing
                        + "\u0000" + effectiveFilter.itemType()
                        + "\u0000" + effectiveFilter.text();

        CompletableFuture<LimitedFolderContent> existing =
                inFlightLoads.get(loadKey);

        if (existing != null) {
            return existing;
        }

        CompletableFuture<LimitedFolderContent> created =
                CompletableFuture.supplyAsync(
                        () -> loadFromS3(
                                bucket,
                                prefix,
                                fileLimit,
                                sortSpec,
                                discoveryListener,
                                flatBrowsing,
                                effectiveFilter),
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

    /**
     * Returns a complete cached result.
     *
     * <p>
     * A result that stopped because of fileLimit cannot be treated
     * as a complete cache.
     * </p>
     */
    public LimitedFolderContent getCachedContent(
            String bucket,
            String prefix) {

        return getCachedContent(bucket, prefix, false);
    }

    public LimitedFolderContent getCachedContent(
            String bucket,
            String prefix,
            boolean flatBrowsing) {

        LimitedFolderContent content = cachedContent;

        if (content == null
                || !Objects.equals(cachedBucket, bucket)
                || !Objects.equals(cachedPrefix, prefix)
                || cachedFlatBrowsing != flatBrowsing
                || content.fileLimitReached()) {
            return null;
        }

        return content;
    }

    /**
     * Invalidates one folder's completed cache.
     */
    public void invalidate(
            String bucket,
            String prefix) {

        if (!Objects.equals(
                cachedBucket,
                bucket)) {

            return;
        }

        if (!Objects.equals(
                cachedPrefix,
                prefix)) {

            return;
        }

        cachedContent = null;
        cachedBucket = null;
        cachedPrefix = null;
    }

    /**
     * Performs the actual S3 listing.
     *
     * <p>
     * This is intentionally the only S3 content-listing call
     * in this loader. The returned LimitedFolderContent contains
     * both folders and files.
     * </p>
     */

    private LimitedFolderContent loadFromS3(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing,
            FileTableFilter filter) {

        S3ExplorerService service =
                Objects.requireNonNull(
                        serviceSupplier.get(),
                        "serviceSupplier returned null");

        LimitedFolderContent content =
                service.listFolderWithLimit(
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec,
                        collationKeyCache,
                        discoveryListener,
                        FolderContentMode.FOLDERS_AND_FILES,
                        flatBrowsing,
                        filter);

        boolean unfiltered =
                !filter.hasTextFilter()
                        && filter.itemType() == ItemTypeFilter.ALL;

        /*
         * Yalnızca filtresiz ve tam sonuçları önbelleğe al.
         * Limit nedeniyle kesilmiş sonuç hiçbir zaman tam önbellek değildir.
         */
        if (unfiltered && !content.fileLimitReached()) {
            cachedContent = content;
            cachedBucket = bucket;
            cachedPrefix = prefix;
            cachedFlatBrowsing = flatBrowsing;
        }

        return content;
    }

    /**
     * Creates a display-limited result from a complete
     * cached result without making another S3 request.
     */
    private LimitedFolderContent createDisplayContent(
            LimitedFolderContent cached,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        BoundedSortedFileCollection bounded =
                new BoundedSortedFileCollection(
                        fileLimit,
                        sortSpec.createFileComparator(
                                collationKeyCache));

        bounded.addAll(cached.files());

        boolean fileLimitReached =
                cached.totalMatchingFileCount()
                        > bounded.size();

        return new LimitedFolderContent(
                cached.folders(),
                bounded.toList(),
                fileLimitReached,
                cached.totalMatchingFileCount());
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

        long sortStart = System.nanoTime();

        matchingFiles.sort(
                sortSpec.createFileComparator(
                        collationKeyCache));

        long sortNanos = System.nanoTime() - sortStart;

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

        long resultNanos = System.nanoTime() - resultStart;
        long totalNanos = System.nanoTime() - methodStart;

        System.err.printf(
                "[FILTER PROFILE] scan=%d ms, sort=%d ms, "
                        + "trim=%d ms, resultCreation=%d ms, "
                        + "total=%d ms, matchingFiles=%d, displayedFiles=%d%n",
                TimeUnit.NANOSECONDS.toMillis(scanNanos),
                TimeUnit.NANOSECONDS.toMillis(sortNanos),
                TimeUnit.NANOSECONDS.toMillis(trimNanos),
                TimeUnit.NANOSECONDS.toMillis(resultNanos),
                TimeUnit.NANOSECONDS.toMillis(totalNanos),
                matchingFileCount,
                result.files().size());

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