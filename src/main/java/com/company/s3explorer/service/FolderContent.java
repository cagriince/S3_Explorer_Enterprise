package com.company.s3explorer.service;

import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

public record FolderContent(
        List<String> folders,
        List<S3Object> files) {
}
