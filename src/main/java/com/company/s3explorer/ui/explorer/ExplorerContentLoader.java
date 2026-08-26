package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.BoundedSortedFileCollection;
import com.company.s3explorer.service.FileTableSortSpec;
import com.company.s3explorer.service.FolderDiscoveryListener;
import com.company.s3explorer.service.LimitedFolderContent;
import com.company.s3explorer.service.S3ExplorerService;

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
 * <p>The loader deliberately has no Swing dependency. A single
 * loadFolder(...) operation obtains both direct folders and files through
 * LimitedFolderContent. ExplorerPanel can therefore use the same S3 result
 * for the folder tree and the file table.</p>
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
     * We intentionally keep the cache scoped to one bucket/prefix for now.
     * This matches the current ExplorerPanel cache semantics and keeps this
     * refactor low-risk.
     */
    private volatile LimitedFolderContent cachedContent;

    private volatile String cachedBucket;

    private volatile String cachedPrefix;

    public ExplorerContentLoader(
            Supplier<S3ExplorerService> serviceSupplier,
            Map<String, CollationKey> collationKeyCache) {

        this.serviceSupplier =
                Objects.requireNonNull(
                        serviceSupplier,
                        "serviceSupplier");

        this.collationKeyCache =
                Objects.requireNonNull(
                        collationKeyCache,
                        "collationKeyCache");
    }

    /**
     * Loads both folders and files.
     *
     * <p>The ExecutorService is supplied by ExplorerPanel because that pool
     * can be resized at runtime.</p>
     */
    public CompletableFuture<LimitedFolderContent> loadFolder(
            ExecutorService executor,
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
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

        Objects.requireNonNull(
                sortSpec,
                "sortSpec");

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        LimitedFolderContent cached =
                getCachedContent(
                        bucket,
                        prefix);

        if (cached != null) {
            return CompletableFuture.completedFuture(
                    createDisplayContent(
                            cached,
                            fileLimit,
                            sortSpec));
        }

        String loadKey =
                createLoadKey(
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec);

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
                                discoveryListener),
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

    /**
     * Returns a complete cached result.
     *
     * <p>A result that stopped because of fileLimit cannot be treated as a
     * complete cache.</p>
     */
    public LimitedFolderContent getCachedContent(
            String bucket,
            String prefix) {

        LimitedFolderContent content =
                cachedContent;

        if (content == null) {
            return null;
        }

        if (!Objects.equals(
                cachedBucket,
                bucket)) {
            return null;
        }

        if (!Objects.equals(
                cachedPrefix,
                prefix)) {
            return null;
        }

        if (content.fileLimitReached()) {
            return null;
        }

        return content;
    }

    public boolean isCached(
            String bucket,
            String prefix) {

        return getCachedContent(
                bucket,
                prefix) != null;
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
     * Invalidates all cached/in-flight bookkeeping.
     */
    public void invalidateAll() {

        cachedContent = null;
        cachedBucket = null;
        cachedPrefix = null;

        inFlightLoads.clear();

        collationKeyCache.clear();
    }

    public int getInFlightLoadCount() {
        return inFlightLoads.size();
    }

    /**
     * Performs the actual S3 listing.
     *
     * <p>This is intentionally the only S3 content-listing call in this
     * loader. The returned LimitedFolderContent contains both folders and
     * files.</p>
     */
    private LimitedFolderContent loadFromS3(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener) {

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
                        discoveryListener);

        /*
         * Only a complete scan may become the reusable full cache.
         */
        if (!content.fileLimitReached()) {

            cachedContent =
                    content;

            cachedBucket =
                    bucket;

            cachedPrefix =
                    prefix;
        }

        return content;
    }

    /**
     * Creates a display-limited result from a complete cached result without
     * making another S3 request.
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

        bounded.addAll(
                cached.files());

        boolean fileLimitReached =
                cached.scannedFileCount()
                        > bounded.size();

        return new LimitedFolderContent(
                cached.folders(),
                bounded.toList(),
                fileLimitReached,
                cached.scannedFileCount());
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