package com.company.s3explorer.service;

import com.company.s3explorer.security.CrossRepositoryEncryptionOptions;
import com.company.s3explorer.security.EncryptionConfig;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class S3ExplorerService {
    private final S3FolderPropertiesService folderPropertiesService;
    private final S3FileTransferService fileTransferService;
    private final S3ObjectOperationService objectOperationService;
    private final S3Client client;

    public S3ExplorerService(S3Client client) {
        this.client = client;

        this.folderPropertiesService =
                new S3FolderPropertiesService(client);

        this.fileTransferService =
                new S3FileTransferService(client);

        this.objectOperationService =
                new S3ObjectOperationService(client);
    }

    public S3Client getClient() {
        return client;
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
                            String folderKey = key.substring(0, slash + 1);

                            if (filter.includesFolders()) {
                                String displayName = getFilterDisplayName(
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
                                String displayName = getFilterDisplayName(
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

    public HeadObjectResponse getObject(String bucket, String key) {
        try {
            HeadObjectRequest request =
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();

            return client.headObject(request);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }

            throw e;
        }
    }

    // ----------------------------------------------------------------------
    // STREAMING LIST API
    // ----------------------------------------------------------------------
    public void forEachObject(
            String bucket,
            String prefix,
            Consumer<S3Object> consumer,
            java.util.function.BooleanSupplier shouldContinue) {

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

            /*
             * Cancel All sırasında paginator'ın bir sonraki
             * sayfasına geçmeden önce listing'i tamamen kes.
             */
            if (!shouldContinue.getAsBoolean()) {

                throw new java.util.concurrent.CancellationException(
                        "S3 object listing cancelled");
            }

            for (S3Object object : page.contents()) {

                /*
                 * Aynı sayfa içerisinde bile cancellation
                 * gelmiş olabilir.
                 */
                if (!shouldContinue.getAsBoolean()) {

                    throw new java.util.concurrent.CancellationException(
                            "S3 object listing cancelled");
                }

                consumer.accept(object);
            }
        }
    }

    // ----------------------------------------------------------------------
    // OBJECT OPERATIONS
    // ----------------------------------------------------------------------

    public void copyObject(
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey) {

        objectOperationService.copyObject(
                sourceBucket,
                sourceKey,
                targetBucket,
                targetKey);
    }

    public void copyObjectOverwrite(
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey) {

        objectOperationService.copyObjectOverwrite(
                sourceBucket,
                sourceKey,
                targetBucket,
                targetKey);
    }

    public void copyObjectBetweenRepositories(
            String sourceBucket,
            String sourceKey,
            S3Client targetRepositoryClient,
            String targetBucket,
            String targetKey,
            TransferProgressListener listener,
            boolean overwrite,
            CrossRepositoryEncryptionOptions options) throws Exception {

        objectOperationService.copyObjectBetweenRepositories(
                sourceBucket,
                sourceKey,
                targetRepositoryClient,
                targetBucket,
                targetKey,
                listener,
                overwrite,
                options);
    }

    public void deleteObject(
            String bucket,
            String objectKey) {

        objectOperationService.deleteObject(
                bucket,
                objectKey);
    }

    public void createFolder(
            String bucket,
            String folderPrefix) {

        objectOperationService.createFolder(
                bucket,
                folderPrefix);
    }

    // ----------------------------------------------------------------------
    // EXISTS
    // ----------------------------------------------------------------------

    public boolean objectExists(
            String bucket,
            String key) {

        return objectOperationService.objectExists(
                bucket,
                key);
    }

    public boolean objectExists(
            S3Client client,
            String bucket,
            String key) {

        return objectOperationService.objectExists(
                client,
                bucket,
                key);
    }

    public boolean folderExists(
            String bucket,
            String fullPrefix) {

        return objectOperationService.folderExists(
                bucket,
                fullPrefix);
    }

    // ----------------------------------------------------------------------
    // DOWNLOAD
    // ----------------------------------------------------------------------

    public void downloadFile(
            String bucket,
            String objectKey,
            Path target,
            TransferProgressListener listener)
            throws IOException {

        fileTransferService.downloadFile(
                bucket,
                objectKey,
                target,
                listener);
    }

    public void downloadDecryptedFile(
            String bucket,
            String objectKey,
            Path target,
            EncryptionConfig encryptionConfig,
            TransferProgressListener listener)
            throws IOException {

        fileTransferService.downloadDecryptedFile(
                bucket,
                objectKey,
                target,
                encryptionConfig,
                listener);
    }

    public void uploadFile(
            String bucket,
            String objectKey,
            Path file,
            boolean overwrite,
            TransferProgressListener listener) throws IOException {

        fileTransferService.uploadFile(
                bucket,
                objectKey,
                file,
                overwrite,
                listener);
    }

    public void uploadEncryptedFile(
            String bucket,
            String objectKey,
            Path file,
            EncryptionConfig encryptionConfig,
            boolean overwrite,
            TransferProgressListener listener) throws Exception {

        fileTransferService.uploadEncryptedFile(
                bucket,
                objectKey,
                file,
                encryptionConfig,
                overwrite,
                listener);
    }

    public void testBucketAccess(
            String bucket) {

        objectOperationService.testBucketAccess(bucket);
    }

    public FolderProperties calculateFolderProperties(
            String bucket,
            String prefix,
            Consumer<FolderProperties> progressConsumer) {

        return folderPropertiesService.calculateFolderProperties(
                bucket,
                prefix,
                progressConsumer);
    }

    public FolderContentSnapshot listFolderSnapshot(
            String bucket,
            String prefix,
            FolderDiscoveryListener discoveryListener,
            boolean flatBrowsing) {

        Objects.requireNonNull(bucket, "bucket");

        String effectivePrefix = prefix == null ? "" : prefix;

        Set<String> folders = new LinkedHashSet<>();
        List<S3Object> files = new ArrayList<>();

        String continuationToken = null;
        long discoveredFileCount = 0;

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

            if (!flatBrowsing) {
                for (CommonPrefix commonPrefix : response.commonPrefixes()) {
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
                        folders.add(key.substring(0, slash + 1));
                        slash = key.indexOf('/', slash + 1);
                    }
                }

                /*
                 * S3 folder-marker objects represent folders, not files.
                 */
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

            continuationToken = response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank());

        return new FolderContentSnapshot(
                new ArrayList<>(folders),
                files);
    }
}