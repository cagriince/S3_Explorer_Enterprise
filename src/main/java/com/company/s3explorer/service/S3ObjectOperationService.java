
package com.company.s3explorer.service;

import com.company.s3explorer.security.CrossRepositoryEncryptionOptions;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class S3ObjectOperationService {

    private final S3Client client;

    public S3ObjectOperationService(S3Client client) {
        this.client = client;
    }

    public void copyObject(
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey) {

        if (objectExists(targetBucket, targetKey)) {
            throw new RuntimeException(
                    "Already exists: " + targetBucket + "/" + targetKey);
        }

        client.copyObject(
                CopyObjectRequest.builder()
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(targetBucket)
                        .destinationKey(targetKey)
                        .build());
    }

    public void copyObjectOverwrite(
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey) {

        client.copyObject(
                CopyObjectRequest.builder()
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(targetBucket)
                        .destinationKey(targetKey)
                        .build());
    }

    public void copyObjectBetweenRepositories(
            String sourceBucket,
            String sourceKey,
            S3Client targetRepositoryClient,
            String targetBucket,
            String targetKey,
            TransferProgressListener listener,
            boolean overwrite,
            CrossRepositoryEncryptionOptions options)
            throws Exception {

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

        HeadObjectResponse head = getObject(sourceBucket, sourceKey);
        long sourceSize = head.contentLength();

        try (InputStream raw =
                     client.getObject(
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
             * S3 source -> optional decrypt -> optional encrypt -> target
             *
             * No intermediate plaintext file is created.
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
            InputStream transformedStream)
            throws IOException {

        final int partSize = 8 * 1024 * 1024;

        CreateMultipartUploadResponse initiated =
                targetClient.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build());

        String uploadId = initiated.uploadId();

        List<CompletedPart> completedParts = new ArrayList<>();
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

        client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build());
    }

    public void createFolder(String bucket, String folderPrefix) {

        if (!folderPrefix.endsWith("/")) {
            folderPrefix += "/";
        }

        if (folderExists(bucket, folderPrefix)) {
            throw new RuntimeException(
                    "Already exists: " + folderPrefix);
        }

        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(folderPrefix)
                        .contentLength(0L)
                        .build(),
                RequestBody.empty());
    }

    public boolean objectExists(String bucket, String key) {
        return objectExists(client, bucket, key);
    }

    public boolean objectExists(
            S3Client targetClient,
            String bucket,
            String key) {

        try {
            targetClient.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

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

    private boolean hasObjectsWithPrefix(
            String bucket,
            String prefix) {

        ListObjectsV2Response response =
                client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .maxKeys(1)
                                .build());

        return !response.contents().isEmpty();
    }

    public boolean folderExists(
            String bucket,
            String fullPrefix) {

        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        return objectExists(bucket, fullPrefix)
                || hasObjectsWithPrefix(bucket, fullPrefix);
    }

    public void testBucketAccess(String bucket) {

        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException(
                    "Bucket name is required");
        }

        client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .maxKeys(1)
                        .build());
    }

    private HeadObjectResponse getObject(
            String bucket,
            String key) {

        try {
            return client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

        } catch (NoSuchKeyException e) {
            return null;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }

            throw e;
        }
    }
}
