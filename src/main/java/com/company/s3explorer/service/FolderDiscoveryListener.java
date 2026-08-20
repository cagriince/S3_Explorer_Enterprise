package com.company.s3explorer.service;

@FunctionalInterface
public interface FolderDiscoveryListener {

    void onDiscovery(
            long fileCount,
            long folderCount);
}