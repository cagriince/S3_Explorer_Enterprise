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
    private final S3ObjectListingService objectListingService;
    private final S3Client client;

    public S3ExplorerService(S3Client client) {
        this.client = client;

        this.folderPropertiesService =
                new S3FolderPropertiesService(client);

        this.fileTransferService =
                new S3FileTransferService(client);

        this.objectOperationService =
                new S3ObjectOperationService(client);

        this.objectListingService =
                new S3ObjectListingService(client);
    }

    public S3Client getClient() {
        return client;
    }

    public List<String> listBuckets() {
        return objectListingService.listBuckets();
    }

    public LimitedFolderContent listFolderWithLimit(
            String bucket,
            String prefix,
            int fileLimit,
            FileTableSortSpec sortSpec,
            FolderDiscoveryListener discoveryListener,
            FolderContentMode contentMode) {

        return objectListingService.listFolderWithLimit(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                discoveryListener,
                contentMode);
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

        return objectListingService.listFolderWithLimit(
                bucket,
                prefix,
                fileLimit,
                sortSpec,
                discoveryListener,
                contentMode,
                flatBrowsing,
                filter);
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

        objectListingService.forEachObject(
                bucket,
                prefix,
                consumer,
                shouldContinue);
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

        return objectListingService.listFolderSnapshot(
                bucket,
                prefix,
                discoveryListener,
                flatBrowsing);
    }
}