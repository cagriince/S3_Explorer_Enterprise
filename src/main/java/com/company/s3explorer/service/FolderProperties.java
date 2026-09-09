package com.company.s3explorer.service;

public record FolderProperties(
        long folderCount,
        long fileCount,
        long totalSize) {
}