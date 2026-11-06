package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class S3FolderPropertiesService {

    private final S3Client client;

    public S3FolderPropertiesService(S3Client client) {
        this.client = client;
    }

    public FolderProperties calculateFolderProperties(
            String bucket,
            String prefix,
            Consumer<FolderProperties> progressConsumer) {

        Set<String> folders = new HashSet<>();

        if (prefix != null && !prefix.isBlank()) {
            folders.add(prefix);
        }

        long fileCount = 0;
        long totalSize = 0;

        for (ListObjectsV2Response page :
                client.listObjectsV2Paginator(
                        ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .build())) {

            for (S3Object object : page.contents()) {

                String key = object.key();

                if (key.endsWith("/")) {
                    folders.add(key);
                } else {
                    fileCount++;
                    totalSize += object.size();

                    addParentFolders(
                            prefix,
                            key,
                            folders);
                }

                if (progressConsumer != null
                        && fileCount % 250 == 0) {

                    progressConsumer.accept(
                            new FolderProperties(
                                    folders.size(),
                                    fileCount,
                                    totalSize));
                }
            }
        }

        FolderProperties result =
                new FolderProperties(
                        folders.size(),
                        fileCount,
                        totalSize);

        if (progressConsumer != null) {
            progressConsumer.accept(result);
        }

        return result;
    }

    private void addParentFolders(
            String rootPrefix,
            String key,
            Set<String> folders) {

        int index = key.lastIndexOf('/');

        while (index >= 0) {

            String folder =
                    key.substring(0, index + 1);

            if (folder.length() > rootPrefix.length()) {
                folders.add(folder);
            }

            index = key.lastIndexOf('/', index - 1);
        }
    }
}