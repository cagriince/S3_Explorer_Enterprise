package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.*;

import java.text.CollationKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
            boolean flatBrowsing) {

        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(sortSpec, "sortSpec");

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        LimitedFolderContent cached =
                getCachedContent(bucket, prefix, flatBrowsing);

        if (cached != null) {
            return CompletableFuture.completedFuture(
                    createDisplayContent(cached, fileLimit, sortSpec));
        }

        String loadKey =
                createLoadKey(bucket, prefix, fileLimit, sortSpec)
                        + "\u0000" + flatBrowsing;

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
                                flatBrowsing),
                        executor);

        CompletableFuture<LimitedFolderContent> actual =
                inFlightLoads.putIfAbsent(loadKey, created);

        if (actual != null) {
            return actual;
        }

        created.whenComplete(
                (result, throwable) ->
                        inFlightLoads.remove(loadKey, created));

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
            boolean flatBrowsing) {

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
                        flatBrowsing);

        if (!content.fileLimitReached()) {
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