package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.service.*;

import java.text.CollationKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Loads folder content from S3 and keeps the loading/cache concerns out of
 * ExplorerPanel.
 *
 * <p>The loader deliberately has no Swing dependency. ExplorerPanel remains
 * responsible for deciding when/how the result is applied to the UI.</p>
 *
 * <p>A single {@link #loadFolder(String, String, int, FileTableSortSpec)}
 * operation returns both folders and files through {@link LimitedFolderContent}.
 * This is the foundation for using one S3 listing result for both the folder
 * tree and the file table.</p>
 */
public final class ExplorerContentLoader {

    private final S3ExplorerService service;
    private final ExecutorService executor;

    /**
     * Prevents duplicate concurrent requests for the same bucket/prefix/limit/
     * sort combination.
     */
    private final Map<String, CompletableFuture<LimitedFolderContent>>
            inFlightLoads = new ConcurrentHashMap<>();

    /**
     * Completed, unlimited folder contents. We only cache content when the S3
     * scan was not stopped by the file limit.
     */
    private volatile LimitedFolderContent cachedContent;
    private volatile String cachedBucket;
    private volatile String cachedPrefix;

    /**
     * Shared with the caller so sorting a previously cached full result keeps
     * the same collation-key cache used by the existing ExplorerPanel.
     */
    private final Map<String, CollationKey> collationKeyCache;

    public ExplorerContentLoader(
            S3ExplorerService service,
            ExecutorService executor,
            Map<String, CollationKey> collationKeyCache) {

        this.service =
                Objects.requireNonNull(
                        service,
                        "service");

        this.executor =
                Objects.requireNonNull(
                        executor,
                        "executor");

        this.collationKeyCache =
                Objects.requireNonNull(
                        collationKeyCache,
                        "collationKeyCache");
    }

    /**
     * Loads both the direct folders and bounded file list for a folder.
     *
     * <p>If the complete content is already cached, no S3 request is made.
     * Otherwise, an identical in-flight request is shared by all callers.</p>
     */
    public CompletableFuture<LimitedFolderContent> loadFolder(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener progressListener) {

        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(sortSpec, "sortSpec");

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
                                progressListener),
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
     * Convenience overload when progress reporting is not needed.
     */
    public CompletableFuture<LimitedFolderContent> loadFolder(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        return loadFolder(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                null);
    }

    /**
     * Returns direct child folders from a single folder-content listing.
     *
     * <p>This method intentionally delegates to loadFolder so
     * callers do not create a second S3 listing just for the tree.</p>
     */
    public CompletableFuture<java.util.List<String>> loadFolders(
            String bucket,
            String prefix,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener progressListener) {

        /*
         * Use Integer.MAX_VALUE here only as a compatibility fallback for
         * tree-only callers. The preferred path is to share the same
         * loadFolder(...) call with the file-table limit selected by the UI.
         */
        return loadFolder(
                bucket,
                prefix,
                Integer.MAX_VALUE,
                sortSpec,
                progressListener)
                .thenApply(LimitedFolderContent::folders);
    }

    /**
     * Checks whether a complete result is available for bucket/prefix.
     */
    public boolean isCached(
            String bucket,
            String prefix) {

        return getCachedContent(
                bucket,
                prefix) != null;
    }

    /**
     * Returns the complete cached result for bucket/prefix, or null.
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

    /**
     * Invalidates the cached complete result for the specified folder.
     */
    public void invalidate(
            String bucket,
            String prefix) {

        if (!Objects.equals(
                cachedBucket,
                bucket)
                || !Objects.equals(
                cachedPrefix,
                prefix)) {
            return;
        }

        cachedContent = null;
        cachedBucket = null;
        cachedPrefix = null;
    }

    /**
     * Invalidates all completed content.
     */
    public void invalidateAll() {
        cachedContent = null;
        cachedBucket = null;
        cachedPrefix = null;
        collationKeyCache.clear();
    }

    /**
     * Removes all currently tracked in-flight requests.
     *
     * <p>This does not forcibly interrupt the underlying S3 call. Existing
     * futures are allowed to finish, but they are no longer considered part of
     * the current loading lifecycle by a new loader instance/request.</p>
     */
    public void clearInFlightLoads() {
        inFlightLoads.clear();
    }

    /**
     * Returns the number of currently shared in-flight loads.
     */
    public int getInFlightLoadCount() {
        return inFlightLoads.size();
    }

    private LimitedFolderContent loadFromS3(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener progressListener) {

        LimitedFolderContent content =
                service.listFolderWithLimit(
                        bucket,
                        prefix,
                        fileLimit,
                        sortSpec,
                        collationKeyCache,
                        progressListener);

        /*
         * Only a complete scan can safely become the reusable cache.
         */
        if (!content.fileLimitReached()) {
            cachedContent = content;
            cachedBucket = bucket;
            cachedPrefix = prefix;
        }

        return content;
    }

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