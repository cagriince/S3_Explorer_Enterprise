package com.company.s3explorer.ui.explorer;

import java.time.Instant;

public class S3FileItem {
    private static String PARENT_FOLDER_NAME = "..";
    private final String repositoryName;
    private final String bucket;
    private final String key;
    private final long size;
    private final Instant lastModified;
    private final boolean folder;

    public S3FileItem(String repositoryName, String bucket, String key, long size, Instant lastModified, boolean folder) {
        this.repositoryName = repositoryName;
        this.bucket = bucket;
        this.key = key;
        this.size = size;
        this.lastModified = lastModified;
        this.folder = folder;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public long getSize() {
        return size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public boolean isFolder() {
        return folder;
    }

    public boolean isFolderButNotParent() {
        return this.isFolder() && !this.isParentFolder();
    }

    public boolean isFile() {
        return !folder;
    }

    public String getName() {
        int index = key.lastIndexOf('/');
        if (index < 0) {
            return key;
        }

        return key.substring(index + 1);
    }

    public boolean isParentFolder() {
        return folder && "..".equals(key);
    }
}