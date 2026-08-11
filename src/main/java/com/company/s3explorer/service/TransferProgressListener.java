package com.company.s3explorer.service;

@FunctionalInterface
public interface TransferProgressListener {
    void update(long transferredBytes, long totalBytes);
}
