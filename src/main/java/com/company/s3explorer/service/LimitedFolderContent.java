package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

public record LimitedFolderContent(
        List<String> folders,
        List<S3Object> files,
        boolean fileLimitReached,
        long totalMatchingFileCount) {

    public LimitedFolderContent {
        folders = folders == null
                ? List.of()
                : List.copyOf(folders);

        files = files == null
                ? List.of()
                : List.copyOf(files);

        if (totalMatchingFileCount < 0) {
            throw new IllegalArgumentException(
                    "totalMatchingFileCount must not be negative");
        }
    }

    public int fileCount() {
        return files.size();
    }

    public int folderCount() {
        return folders.size();
    }
}