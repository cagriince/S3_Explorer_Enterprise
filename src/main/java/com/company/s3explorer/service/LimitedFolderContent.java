package com.company.s3explorer.service;

import com.company.s3explorer.ui.explorer.S3FileItem;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

public record LimitedFolderContent(
        List<String> folders,
        List<S3Object> files,
        boolean fileLimitReached,
        long scannedFileCount) {

    public int fileCount() {
        return files.size();
    }

    public int folderCount() {
        return folders.size();
    }
}