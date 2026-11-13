
package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class S3ObjectListingService {

    private final S3Client client;

    public S3ObjectListingService(S3Client client) {
        this.client = client;
    }

    public List<String> listBuckets() {
        return client.listBuckets()
                .buckets()
                .stream()
                .map(Bucket::name)
                .toList();
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            FolderContentMode contentMode) {

        return listFolderWithLimit(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                discoveryListener,
                contentMode,
                false,
                FileTableFilter.noFilter());
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            FolderContentMode contentMode,
            boolean flatBrowsing,
            FileTableFilter filter) {

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        if (sortSpec == null) {
            sortSpec = FileTableSortSpec.defaultSpec();
        }

        if (contentMode == null) {
            contentMode = FolderContentMode.FOLDERS_AND_FILES;
        }

        if (filter == null) {
            filter = FileTableFilter.noFilter();
        }

        String effectivePrefix =
                prefix == null ? "" : prefix;

        Set<String> folders = new LinkedHashSet<>();

        BoundedSortedFileCollection files =
                contentMode == FolderContentMode.FOLDERS_AND_FILES
                        && filter.includesFiles()
                        ? new BoundedSortedFileCollection(
                        fileLimit,
                        sortSpec.createFileComparator())
                        : null;

        long matchingFileCount = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder builder =
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(effectivePrefix)
                            .maxKeys(500);

            if (!flatBrowsing) {
                builder.delimiter("/");
            }

            if (continuationToken != null
                    && !continuationToken.isBlank()) {
                builder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response =
                    client.listObjectsV2(builder.build());

            if (contentMode == FolderContentMode.FOLDERS_AND_FILES
                    && filter.includesFolders()) {

                if (!flatBrowsing) {
                    for (CommonPrefix commonPrefix
                            : response.commonPrefixes()) {

                        String folderKey = commonPrefix.prefix();

                        String displayName = getFilterDisplayName(
                                folderKey,
                                effectivePrefix,
                                true,
                                false);

                        if (filter.matchesText(displayName)) {
                            folders.add(folderKey);
                        }
                    }
                }

            } else if (contentMode == FolderContentMode.FOLDERS_ONLY) {

                if (!flatBrowsing) {
                    for (CommonPrefix commonPrefix
                            : response.commonPrefixes()) {

                        folders.add(commonPrefix.prefix());
                    }
                }
            }

            if (contentMode == FolderContentMode.FOLDERS_AND_FILES) {

                for (S3Object object : response.contents()) {

                    String key = object.key();

                    if (key.equals(effectivePrefix)) {
                        continue;
                    }

                    if (flatBrowsing) {

                        int slash = key.indexOf(
                                '/',
                                effectivePrefix.length());

                        while (slash >= 0) {

                            String folderKey =
                                    key.substring(0, slash + 1);

                            if (filter.includesFolders()) {

                                String displayName =
                                        getFilterDisplayName(
                                                folderKey,
                                                effectivePrefix,
                                                true,
                                                true);

                                if (filter.matchesText(displayName)) {
                                    folders.add(folderKey);
                                }
                            }

                            slash = key.indexOf('/', slash + 1);
                        }

                        if (key.endsWith("/")) {

                            if (filter.includesFolders()) {

                                String displayName =
                                        getFilterDisplayName(
                                                key,
                                                effectivePrefix,
                                                true,
                                                true);

                                if (filter.matchesText(displayName)) {
                                    folders.add(key);
                                }
                            }

                            continue;
                        }

                    } else if (key.endsWith("/")) {
                        continue;
                    }

                    if (!filter.includesFiles()) {
                        continue;
                    }

                    String displayName = getFilterDisplayName(
                            key,
                            effectivePrefix,
                            false,
                            flatBrowsing);

                    if (!filter.matchesText(displayName)) {
                        continue;
                    }

                    matchingFileCount++;
                    files.add(object);
                }
            }

            if (discoveryListener != null) {
                discoveryListener.onDiscovery(
                        matchingFileCount,
                        folders.size());
            }

            continuationToken = response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank());

        List<S3Object> fileList =
                files == null
                        ? List.of()
                        : files.toList();

        boolean fileLimitReached =
                matchingFileCount > fileList.size();

        return new LimitedFolderContent(
                new ArrayList<>(folders),
                fileList,
                fileLimitReached,
                matchingFileCount);
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

    public void forEachObject(
            String bucket,
            String prefix,
            Consumer<S3Object> consumer,
            BooleanSupplier shouldContinue) {

        if (consumer == null) {
            throw new IllegalArgumentException(
                    "consumer must not be null");
        }

        if (shouldContinue == null) {
            throw new IllegalArgumentException(
                    "shouldContinue must not be null");
        }

        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build();

        ListObjectsV2Iterable iterable =
                client.listObjectsV2Paginator(request);

        for (ListObjectsV2Response page : iterable) {

            if (!shouldContinue.getAsBoolean()) {
                System.out.println(
                        "[S3 LISTING] Cancelled: bucket={}, prefix={}" +
                        bucket + " : " + prefix
                );
                throw new CancellationException(
                        "S3 object listing cancelled");
            }

            for (S3Object object : page.contents()) {

                if (!shouldContinue.getAsBoolean()) {
                    System.out.println(
                            "[S3 LISTING] Cancelled: bucket={}, prefix={}" +
                            bucket + " : " + prefix
                    );
                    throw new CancellationException(
                            "S3 object listing cancelled");
                }

                consumer.accept(object);
            }
        }
    }

    public FolderContentSnapshot listFolderSnapshot(
            String bucket,
            String prefix,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing) {

        return listFolderSnapshot(
                bucket,
                prefix,
                discoveryListener,
                flatBrowsing,
                () -> true);
    }

    public FolderContentSnapshot listFolderSnapshot(
            String bucket,
            String prefix,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing,
            BooleanSupplier shouldContinue) {

        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(shouldContinue, "shouldContinue");

        String effectivePrefix =
                prefix == null ? "" : prefix;

        Set<String> folders = new LinkedHashSet<>();
        List<S3Object> files = new ArrayList<>();

        String continuationToken = null;
        long discoveredFileCount = 0;

        do {
            if (!shouldContinue.getAsBoolean()) {
                System.out.println(
                        "[S3 LISTING] Cancelled: bucket={}, prefix={}" +
                        bucket + " : " + prefix
                );
                throw new CancellationException(
                        "S3 folder listing cancelled");
            }

            ListObjectsV2Request.Builder builder =
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(effectivePrefix)
                            .maxKeys(500);

            if (!flatBrowsing) {
                builder.delimiter("/");
            }

            if (continuationToken != null
                    && !continuationToken.isBlank()) {
                builder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response =
                    client.listObjectsV2(builder.build());

            /*
             * The current HTTP request may have completed after
             * cancellation was requested. Do not process its result.
             */
            if (!shouldContinue.getAsBoolean()) {
                System.out.println(
                        "[S3 LISTING] Cancelled: bucket={}, prefix={}" +
                        bucket + " : " + prefix
                );
                throw new CancellationException(
                        "S3 folder listing cancelled");
            }

            if (!flatBrowsing) {
                for (CommonPrefix commonPrefix
                        : response.commonPrefixes()) {

                    folders.add(commonPrefix.prefix());
                }
            }

            for (S3Object object : response.contents()) {

                String key = object.key();

                if (key.equals(effectivePrefix)) {
                    continue;
                }

                if (flatBrowsing) {

                    int slash = key.indexOf(
                            '/',
                            effectivePrefix.length());

                    while (slash >= 0) {

                        folders.add(
                                key.substring(0, slash + 1));

                        slash = key.indexOf('/', slash + 1);
                    }
                }

                if (key.endsWith("/")) {
                    folders.add(key);
                    continue;
                }

                files.add(object);
                discoveredFileCount++;
            }

            if (discoveryListener != null) {
                discoveryListener.onDiscovery(
                        discoveredFileCount,
                        folders.size());
            }

            continuationToken =
                    response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank()
                && shouldContinue.getAsBoolean());

        if (!shouldContinue.getAsBoolean()) {
            System.out.println(
                    "[S3 LISTING] Cancelled: bucket={}, prefix={}" +
                    bucket + " : " + prefix
            );
            throw new CancellationException(
                    "S3 folder listing cancelled");
        }

        return new FolderContentSnapshot(
                new ArrayList<>(folders),
                files);
    }
}
