package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

public record FolderContentPage(
        List<String> folders,
        List<S3Object> files,
        String continuationToken,
        boolean truncated) {

    public boolean hasMore() {
        return truncated
                && continuationToken != null
                && !continuationToken.isBlank();
    }
}