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
    private final S3Client client;

    public S3ExplorerService(S3Client client) {
        this.client = client;

        this.folderPropertiesService =
                new S3FolderPropertiesService(client);

        this.fileTransferService =
                new S3FileTransferService(client);
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

    public void copyObject(String sourceBucket, String sourceKey, String targetBucket, String targetKey) {
        if (objectExists(targetBucket, targetKey)) {
            throw new RuntimeException("Already exists: " + targetBucket + "/" + targetKey);
        }

        CopyObjectRequest request =
                CopyObjectRequest.builder()
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(targetBucket)
                        .destinationKey(targetKey)
                        .build();

        client.copyObject(request);
    }

    public void copyObjectOverwrite(
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey) {

        CopyObjectRequest request =
                CopyObjectRequest.builder()
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(targetBucket)
                        .destinationKey(targetKey)
                        .build();

        client.copyObject(request);
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

        if (options == null) {
            options = CrossRepositoryEncryptionOptions.none();
        }

        if (!overwrite
                && objectExists(
                targetRepositoryClient,
                targetBucket,
                targetKey)) {

            throw new RuntimeException(
                    "Already exists: " + targetBucket + "/" + targetKey);
        }

        HeadObjectResponse head =
                getObject(sourceBucket, sourceKey);

        long sourceSize = head.contentLength();

        try (InputStream raw = client.getObject(
                GetObjectRequest.builder()
                        .bucket(sourceBucket)
                        .key(sourceKey)
                        .build())) {

            InputStream source =
                    new ProgressInputStream(
                            raw,
                            sourceSize,
                            listener);

            /*
             * Dönüşümler sıralı uygulanır:
             *
             * S3 source -> optional decrypt -> optional encrypt -> target
             *
             * CipherInputStream akışı zincirler; ara plaintext dosyası
             * oluşturulmaz.
             */
            if (options.isDecryptSource()) {
                source = new CipherInputStream(
                        source,
                        options.getSourceEncryptionConfig()
                                .createCipher(Cipher.DECRYPT_MODE));
            }

            if (options.isEncryptTarget()) {
                source = new CipherInputStream(
                        source,
                        options.getTargetEncryptionConfig()
                                .createCipher(Cipher.ENCRYPT_MODE));
            }

            if (!options.isDecryptSource()
                    && !options.isEncryptTarget()) {

                targetRepositoryClient.putObject(
                        PutObjectRequest.builder()
                                .bucket(targetBucket)
                                .key(targetKey)
                                .build(),
                        RequestBody.fromInputStream(
                                source,
                                sourceSize));

                return;
            }

            uploadTransformedStream(
                    targetRepositoryClient,
                    targetBucket,
                    targetKey,
                    source);
        }
    }

    private void uploadTransformedStream(
            S3Client targetClient,
            String bucket,
            String key,
            InputStream transformedStream) throws IOException {

        final int partSize = 8 * 1024 * 1024;

        CreateMultipartUploadResponse initiated =
                targetClient.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build());

        String uploadId = initiated.uploadId();

        List<CompletedPart> completedParts =
                new ArrayList<>();

        boolean completed = false;

        try (InputStream input = transformedStream) {

            int partNumber = 1;

            while (true) {

                byte[] buffer = new byte[partSize];
                int count = 0;

                while (count < buffer.length) {

                    int read = input.read(
                            buffer,
                            count,
                            buffer.length - count);

                    if (read < 0) {
                        break;
                    }

                    if (read == 0) {
                        continue;
                    }

                    count += read;
                }

                if (count == 0) {
                    break;
                }

                if (partNumber > 10000) {
                    throw new IOException(
                            "The object exceeds the supported multipart upload part count.");
                }

                byte[] partBytes =
                        count == buffer.length
                                ? buffer
                                : Arrays.copyOf(buffer, count);

                UploadPartResponse uploaded =
                        targetClient.uploadPart(
                                UploadPartRequest.builder()
                                        .bucket(bucket)
                                        .key(key)
                                        .uploadId(uploadId)
                                        .partNumber(partNumber)
                                        .contentLength((long) count)
                                        .build(),
                                RequestBody.fromBytes(partBytes));

                completedParts.add(
                        CompletedPart.builder()
                                .partNumber(partNumber)
                                .eTag(uploaded.eTag())
                                .build());

                partNumber++;
            }

            if (completedParts.isEmpty()) {

                targetClient.abortMultipartUpload(
                        AbortMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .uploadId(uploadId)
                                .build());

                completed = true;

                targetClient.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build(),
                        RequestBody.empty());

                return;
            }

            targetClient.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .uploadId(uploadId)
                            .multipartUpload(
                                    CompletedMultipartUpload.builder()
                                            .parts(completedParts)
                                            .build())
                            .build());

            completed = true;

        } finally {

            if (!completed) {
                try {
                    targetClient.abortMultipartUpload(
                            AbortMultipartUploadRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .uploadId(uploadId)
                                    .build());
                } catch (Exception abortException) {
                    // Preserve the original upload exception.
                }
            }
        }
    }

    public void deleteObject(String bucket, String objectKey) {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build();
        client.deleteObject(request);
    }

    public void createFolder(String bucket, String folderPrefix) {
        if (!folderPrefix.endsWith("/")) {
            folderPrefix += "/";
        }

        if (folderExists(bucket, folderPrefix)) {
            throw new RuntimeException("Already exists: " + folderPrefix);
        }

        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(folderPrefix)
                        .contentLength(0L)
                        .build();

        client.putObject(
                request,
                RequestBody.empty());
    }

    // ----------------------------------------------------------------------
    // EXISTS
    // ----------------------------------------------------------------------

    public boolean objectExists(String bucket, String key) {
        return objectExists(client, bucket, key);
    }

    public boolean objectExists(S3Client client, String bucket, String key) {
        try {
            HeadObjectRequest request =
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();

            client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }

            throw e;
        }
    }

    private boolean hasObjectsWithPrefix(String bucket, String prefix) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(1)
                        .build();

        ListObjectsV2Response response = client.listObjectsV2(request);

        return !response.contents().isEmpty();
    }

    public boolean folderExists(String bucket, String fullPrefix) {
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        return objectExists(bucket, fullPrefix) || hasObjectsWithPrefix(bucket, fullPrefix);
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

        if (bucket == null
                || bucket.isBlank()) {

            throw new IllegalArgumentException(
                    "Bucket name is required");
        }

        client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .maxKeys(1)
                        .build());
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