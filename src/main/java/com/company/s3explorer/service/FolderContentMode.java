package com.company.s3explorer.service;

public enum FolderContentMode {

    /**
     * Discover only direct child folders.
     * Files are not collected.
     */
    FOLDERS_ONLY,

    /**
     * Discover direct child folders and files.
     */
    FOLDERS_AND_FILES
}