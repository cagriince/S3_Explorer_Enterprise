package com.company.s3explorer.transfer;

public interface TransferProgressListener {
    void onProgress(
            long transferred,
            long total);
}