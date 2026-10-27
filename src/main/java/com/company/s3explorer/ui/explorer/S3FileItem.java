
package com.company.s3explorer.ui.explorer;

import com.company.s3explorer.ui.icons.FileIconRegistry;
import com.company.s3explorer.util.S3Util;

import java.time.Instant;

public class S3FileItem {

    public static final String PARENT_FOLDER_NAME = "..";

    private final String repositoryId;
    private final String bucket;
    private final String key;
    private final long size;
    private final Instant lastModified;
    private final String storageClass;
    private final boolean folder;
    private final String displayName;

    /*
     * Calculated once when the item is created.
     * Avoids repeatedly extracting the name during table sorting.
     */
    private final String name;
    private final String typeDisplayName;

    public S3FileItem(
            String repositoryId,
            String bucket,
            String key,
            long size,
            Instant lastModified,
            String storageClass,
            boolean folder) {

        this(
                repositoryId,
                bucket,
                key,
                size,
                lastModified,
                storageClass,
                folder,
                null);
    }

    public S3FileItem(String repositoryId, String bucket, String key, long size,
                      Instant lastModified, String storageClass, boolean folder,
                      String displayName) {
        this.repositoryId = repositoryId;
        this.bucket = bucket;
        this.key = key;
        this.size = size;
        this.lastModified = lastModified;
        this.storageClass = storageClass;
        this.folder = folder;
        this.name = calculateName(key, folder);
        this.displayName = displayName;
        this.typeDisplayName = folder
                ? "Folder"
                : FileIconRegistry.findFileType(key).displayName();
    }

    private static String calculateName(
            String key,
            boolean folder) {

        if (key == null) {
            return null;
        }

        if (folder) {
            return S3Util.extractFolderName(key);
        }

        int index = key.lastIndexOf('/');

        if (index < 0) {
            return key;
        }

        return key.substring(index + 1);
    }

    public String getRepositoryId() {
        return repositoryId;
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

    public String getStorageClass() {
        return storageClass;
    }

    public boolean isFolder() {
        return folder;
    }

    public String getDisplayName() {
        return displayName != null
                ? displayName
                : getName();
    }

    public String getName() {
        return name;
    }

    public String getTypeDisplayName() {
        return typeDisplayName;
    }

    public boolean isFile() {
        return !folder;
    }

    public boolean isParentFolder() {
        return folder
                && S3Util.extractFileName(key)
                .equals(PARENT_FOLDER_NAME);
    }
}