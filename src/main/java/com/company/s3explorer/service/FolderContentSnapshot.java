package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

/**
 * Complete, unfiltered folder listing retained for local filtering,
 * sorting, and display-limit processing.
 *
 * Unlike LimitedFolderContent, this snapshot contains every discovered
 * file and folder for the requested browsing mode.
 */
public record FolderContentSnapshot(
        List<String> folders,
        List<S3Object> files) {

    public FolderContentSnapshot {
        folders = folders == null
                ? List.of()
                : List.copyOf(folders);

        files = files == null
                ? List.of()
                : List.copyOf(files);
    }
}