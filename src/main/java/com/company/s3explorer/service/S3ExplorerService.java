package com.company.s3explorer.service;

import com.company.s3explorer.util.S3Util;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.CollationKey;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class S3ExplorerService {
    private final S3Client client;

    public S3ExplorerService(S3Client client) {
        this.client = client;
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

    public FolderContent listFolder(
            String bucket,
            String prefix) {

        List<String> folders =
                new ArrayList<>();

        List<S3Object> files =
                new ArrayList<>();

        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(250)
                        .delimiter("/")
                        .build();

        ListObjectsV2Iterable pages =
                client.listObjectsV2Paginator(request);

        long start = System.currentTimeMillis();

        System.out.println(
                "LIST FOLDER START bucket="
                        + bucket
                        + " prefix="
                        + prefix);

        int pageNo = 0;
        long objectCount = 0;
        long folderCount = 0;
        
        for (ListObjectsV2Response page : pages) {
            pageNo++;

            objectCount +=
                    page.contents().size();

            folderCount +=
                    page.commonPrefixes().size();

            System.out.println(
                    "LIST FOLDER PAGE "
                            + pageNo
                            + " objects="
                            + objectCount
                            + " folders="
                            + folderCount
                            + " elapsed="
                            + (System.currentTimeMillis() - start)
                            + " ms");
            /*
             * Klasörler
             */
            for (CommonPrefix commonPrefix :
                    page.commonPrefixes()) {

                folders.add(
                        commonPrefix.prefix());
            }

            /*
             * Dosyalar
             */
            for (S3Object object :
                    page.contents()) {

                /*
                 * S3 bazen prefix'in kendisini de
                 * contents içinde döndürebilir.
                 */
                if (!object.key().equals(prefix)) {

                    files.add(object);
                }
            }
        }

        return new FolderContent(
                folders,
                files);
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec) {

        return listFolderWithLimit(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                new HashMap<>());
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            Map<String, CollationKey> collationKeyCache) {

        return listFolderWithLimit(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                collationKeyCache,
                null);
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            Map<String, CollationKey> collationKeyCache,
            FolderDiscoveryListener discoveryListener) {

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        if (sortSpec == null) {
            sortSpec =
                    FileTableSortSpec.defaultSpec();
        }

        Set<String> folders =
                new LinkedHashSet<>();

        BoundedSortedFileCollection files =
                new BoundedSortedFileCollection(
                        fileLimit,
                        sortSpec.createFileComparator(
                                collationKeyCache));

        String continuationToken = null;

        long scannedFileCount = 0;

        do {

            ListObjectsV2Request.Builder builder =
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(
                                    prefix == null
                                            ? ""
                                            : prefix)
                            .delimiter("/")
                            .maxKeys(500);

            if (continuationToken != null
                    && !continuationToken.isBlank()) {

                builder.continuationToken(
                        continuationToken);
            }

            ListObjectsV2Response response =
                    client.listObjectsV2(
                            builder.build());

            for (CommonPrefix commonPrefix :
                    response.commonPrefixes()) {

                folders.add(
                        commonPrefix.prefix());
            }

            for (S3Object object :
                    response.contents()) {

                if (object.key().equals(prefix)) {
                    continue;
                }

                if (object.key().endsWith("/")) {
                    continue;
                }

                scannedFileCount++;

                files.add(object);
            }

            /*
             * Bu noktada bir S3 sayfası tamamen işlendi.
             *
             * Her dosyada callback çağırmıyoruz.
             * Böylece UI tarafına gereksiz yük bindirmiyoruz.
             */
            if (discoveryListener != null) {

                discoveryListener.onDiscovery(
                        scannedFileCount,
                        folders.size());
            }

            continuationToken =
                    response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank());

        boolean fileLimitReached =
                scannedFileCount > files.size();

        return new LimitedFolderContent(
                new ArrayList<>(folders),
                files.toList(),
                fileLimitReached,
                scannedFileCount);
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            Map<String, CollationKey> collationKeyCache,
            FolderDiscoveryListener discoveryListener,
            FolderContentMode contentMode) {

        if (fileLimit <= 0) {
            throw new IllegalArgumentException(
                    "fileLimit must be greater than zero");
        }

        if (sortSpec == null) {
            sortSpec =
                    FileTableSortSpec.defaultSpec();
        }

        if (contentMode == null) {
            contentMode =
                    FolderContentMode.FOLDERS_AND_FILES;
        }

        Set<String> folders =
                new LinkedHashSet<>();

        BoundedSortedFileCollection files =
                contentMode == FolderContentMode.FOLDERS_AND_FILES
                        ? new BoundedSortedFileCollection(
                        fileLimit,
                        sortSpec.createFileComparator(
                                collationKeyCache))
                        : null;

        String continuationToken = null;

        long scannedFileCount = 0;

        do {

            ListObjectsV2Request.Builder builder =
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(
                                    prefix == null
                                            ? ""
                                            : prefix)
                            .delimiter("/")
                            .maxKeys(500);

            if (continuationToken != null
                    && !continuationToken.isBlank()) {

                builder.continuationToken(
                        continuationToken);
            }

            ListObjectsV2Response response =
                    client.listObjectsV2(
                            builder.build());

            /*
             * Folders are always collected,
             * regardless of the file limit.
             */
            for (CommonPrefix commonPrefix :
                    response.commonPrefixes()) {

                folders.add(
                        commonPrefix.prefix());
            }

            /*
             * In FOLDERS_ONLY mode we deliberately
             * do not inspect file objects.
             */
            if (contentMode
                    == FolderContentMode.FOLDERS_AND_FILES) {

                for (S3Object object :
                        response.contents()) {

                    if (object.key().equals(prefix)) {
                        continue;
                    }

                    if (object.key().endsWith("/")) {
                        continue;
                    }

                    scannedFileCount++;

                    files.add(object);
                }
            }

            if (discoveryListener != null) {

                discoveryListener.onDiscovery(
                        scannedFileCount,
                        folders.size());
            }

            continuationToken =
                    response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank());

        /*
         * FOLDERS_ONLY never reaches a file limit.
         */
        boolean fileLimitReached =
                contentMode
                        == FolderContentMode.FOLDERS_AND_FILES
                        && scannedFileCount > files.size();

        List<S3Object> fileList =
                contentMode
                        == FolderContentMode.FOLDERS_AND_FILES
                        ? files.toList()
                        : List.of();

        return new LimitedFolderContent(
                new ArrayList<>(folders),
                fileList,
                fileLimitReached,
                scannedFileCount);
    }
    
    public FolderContentPage listFolderPage(
            String bucket,
            String prefix,
            String continuationToken) {

        ListObjectsV2Request.Builder builder =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix == null ? "" : prefix)
                        .delimiter("/")
                        .maxKeys(500);

        if (continuationToken != null
                && !continuationToken.isBlank()) {

            builder.continuationToken(
                    continuationToken);
        }

        ListObjectsV2Response response =
                client.listObjectsV2(
                        builder.build());

        List<String> folders =
                response.commonPrefixes()
                        .stream()
                        .map(CommonPrefix::prefix)
                        .toList();

        List<S3Object> files =
                response.contents()
                        .stream()
                        .filter(object ->
                                !object.key()
                                        .equals(prefix))
                        .toList();

        return new FolderContentPage(
                folders,
                files,
                response.nextContinuationToken(),
                response.isTruncated());
    }

    public List<String> listFolders(
            String bucket,
            String prefix) {

        List<String> folders =
                new ArrayList<>();

        String continuationToken = null;

        do {

            ListObjectsV2Request.Builder builder =
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .delimiter("/")
                            .prefix(
                                    prefix == null
                                            ? ""
                                            : prefix)
                            .maxKeys(500);

            if (continuationToken != null
                    && !continuationToken.isBlank()) {

                builder.continuationToken(
                        continuationToken);
            }

            ListObjectsV2Response response =
                    client.listObjectsV2(
                            builder.build());

            for (CommonPrefix commonPrefix :
                    response.commonPrefixes()) {

                folders.add(
                        commonPrefix.prefix());
            }

            continuationToken =
                    response.nextContinuationToken();

        } while (continuationToken != null
                && !continuationToken.isBlank());

        return folders;
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
/*
    public void forEachObject(String bucket, String prefix, Consumer<S3Object> consumer) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build();

        client.listObjectsV2Paginator(request)
                .stream()
                .flatMap(response -> response.contents().stream())
                .forEach(consumer);
    }*/

    public void forEachObject(String bucket, String prefix, Consumer<S3Object> consumer) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build();

        ListObjectsV2Iterable iterable = client.listObjectsV2Paginator(request);
        for (ListObjectsV2Response page : iterable) {
            for (S3Object object : page.contents()) {
                consumer.accept(object);
            }
        }
    }

    public void forEachFolder(String bucket, String prefix, Consumer<CommonPrefix> consumer) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .delimiter("/")
                        .build();

        ListObjectsV2Iterable iterable = client.listObjectsV2Paginator(request);
        for (ListObjectsV2Response page : iterable) {
            page.commonPrefixes().forEach(consumer);
        }
    }

    public Stream<S3Object> streamObjects(String bucket, String prefix) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build();

        return client
                .listObjectsV2Paginator(request)
                .stream()
                .flatMap(page -> page.contents().stream());
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

    public void copyObjectBetweenRepositories(String sourceBucket, String sourceKey, S3Client targetRepositoryClient, String targetBucket, String targetKey, TransferProgressListener listener) throws IOException {
        if (objectExists(targetRepositoryClient, targetBucket, targetKey)) {
            throw new RuntimeException("Already exists: " + targetBucket + "/" + targetKey);
        }

        HeadObjectResponse head = getObject(sourceBucket, sourceKey);
        long totalBytes = head.contentLength();

        try (InputStream raw = client.getObject(
                GetObjectRequest.builder()
                        .bucket(sourceBucket)
                        .key(sourceKey)
                        .build());
            InputStream progressStream = new ProgressInputStream(raw, totalBytes, listener)) {
                targetRepositoryClient.putObject(PutObjectRequest.builder()
                        .bucket(targetBucket)
                        .key(targetKey)
                        .build(), RequestBody.fromInputStream(progressStream, totalBytes));
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

    public void downloadFile(String bucket, String objectKey, Path target, TransferProgressListener listener) throws IOException {
        if (S3Util.isFolder(objectKey)) {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                //throw new RuntimeException(e);
            }
            listener.update(100, 100);
            return;
        }

        HeadObjectResponse head = getObject(bucket, objectKey);
        long totalBytes = head.contentLength();

        try (InputStream raw = client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
                 InputStream in = new ProgressInputStream(raw, totalBytes, listener);
                 OutputStream out = Files.newOutputStream(target)) {

            Files.createDirectories(target.getParent());
            in.transferTo(out);
        }
    }

    public void uploadFile(String bucket, String objectKey, Path file, TransferProgressListener listener) throws IOException {
        if (objectExists(bucket, objectKey)) {
            throw new RuntimeException("Already exists: " + bucket + "/" + objectKey);
        }

        long totalBytes = Files.size(file);

        try (InputStream in =
                     new ProgressInputStream(
                             Files.newInputStream(file),
                             totalBytes,
                             listener)) {

            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build(),
                    RequestBody.fromInputStream(in, totalBytes));
        }
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
}