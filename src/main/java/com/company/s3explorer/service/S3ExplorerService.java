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
import java.util.List;
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

        ListObjectsV2Response response =
                client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .delimiter("/")
                                .build());

        List<String> folders =
                response.commonPrefixes()
                        .stream()
                        .map(CommonPrefix::prefix)
                        .toList();

        List<S3Object> files =
                response.contents()
                        .stream()
                        .filter(o -> !o.key().equals(prefix))
                        .toList();

        return new FolderContent(folders, files);
    }

    public List<String> listFolders(String bucket, String prefix) {
        ListObjectsV2Request request =
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .delimiter("/")
                        .prefix(prefix == null ? "" : prefix)
                        .build();

        return client.listObjectsV2(request)
                .commonPrefixes()
                .stream()
                .map(CommonPrefix::prefix)
                .toList();
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
}