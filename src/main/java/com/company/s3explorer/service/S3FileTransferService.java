
package com.company.s3explorer.service;

import com.company.s3explorer.util.S3Util;
import com.company.s3explorer.security.EncryptionConfig;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class S3FileTransferService {

    private final S3Client client;

    public S3FileTransferService(S3Client client) {
        this.client = client;
    }

    public void downloadFile(
            String bucket,
            String objectKey,
            Path target,
            TransferProgressListener listener)
            throws IOException {

        if (S3Util.isFolder(objectKey)) {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                // Preserve existing behavior.
            }

            listener.update(100, 100);
            return;
        }

        HeadObjectResponse head = getObject(bucket, objectKey);
        long totalBytes = head.contentLength();

        Files.createDirectories(target.getParent());

        try (InputStream raw =
                     client.getObject(
                             GetObjectRequest.builder()
                                     .bucket(bucket)
                                     .key(objectKey)
                                     .build());
             InputStream in =
                     new ProgressInputStream(
                             raw,
                             totalBytes,
                             listener);
             OutputStream out =
                     Files.newOutputStream(target)) {

            in.transferTo(out);
        }
    }

    public void downloadDecryptedFile(
            String bucket,
            String objectKey,
            Path target,
            EncryptionConfig encryptionConfig,
            TransferProgressListener listener)
            throws IOException {

        if (S3Util.isFolder(objectKey)) {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                // Preserve existing behavior.
            }

            listener.update(100, 100);
            return;
        }

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        Cipher cipher;

        try {
            cipher = encryptionConfig.createCipher(
                    Cipher.DECRYPT_MODE);
        } catch (Exception ex) {
            throw new IOException(
                    "Failed to initialize decryption.",
                    ex);
        }

        HeadObjectResponse head = getObject(bucket, objectKey);
        long encryptedSize = head.contentLength();

        Files.createDirectories(target.getParent());

        try (InputStream raw =
                     client.getObject(
                             GetObjectRequest.builder()
                                     .bucket(bucket)
                                     .key(objectKey)
                                     .build());
             CipherInputStream decryptedInput =
                     new CipherInputStream(raw, cipher);
             OutputStream output =
                     Files.newOutputStream(target)) {

            byte[] buffer = new byte[8192];
            long processed = 0;
            int read;

            while ((read = decryptedInput.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                processed += read;

                listener.update(processed, encryptedSize);
            }
        }
    }

    public void uploadFile(
            String bucket,
            String objectKey,
            Path file,
            boolean overwrite,
            TransferProgressListener listener)
            throws IOException {

        if (!overwrite && objectExists(bucket, objectKey)) {
            throw new RuntimeException(
                    "Already exists: " + bucket + "/" + objectKey);
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

    public void uploadEncryptedFile(
            String bucket,
            String objectKey,
            Path file,
            EncryptionConfig encryptionConfig,
            boolean overwrite,
            TransferProgressListener listener)
            throws Exception {

        if (!overwrite && objectExists(bucket, objectKey)) {
            throw new RuntimeException(
                    "Already exists: " + bucket + "/" + objectKey);
        }

        if (encryptionConfig == null) {
            throw new IllegalArgumentException(
                    "Encryption configuration is not available.");
        }

        Cipher cipher =
                encryptionConfig.createCipher(Cipher.ENCRYPT_MODE);

        long plainSize = Files.size(file);

        long encryptedSize =
                cipher.getOutputSize(Math.toIntExact(plainSize));

        try (InputStream fileInput = Files.newInputStream(file);
             CipherInputStream encryptedInput =
                     new CipherInputStream(fileInput, cipher);
             InputStream progressInput =
                     new ProgressInputStream(
                             encryptedInput,
                             encryptedSize,
                             listener)) {

            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build(),
                    RequestBody.fromInputStream(
                            progressInput,
                            encryptedSize));
        }
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

    private boolean objectExists(
            String bucket,
            String key) {

        try {
            client.headObject(
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
}
