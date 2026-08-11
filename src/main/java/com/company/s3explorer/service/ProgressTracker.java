package com.company.s3explorer.service;

public class ProgressTracker {

    private static final long BUFFER_SIZE = 256 * 1024;

    private final long totalBytes;
    private final TransferProgressListener listener;

    private long transferredBytes;
    private long lastPublished;

    public ProgressTracker(long totalBytes, TransferProgressListener listener) {
        this.totalBytes = totalBytes;
        this.listener = listener;
    }

    public void transferred(long bytes) {
        transferredBytes += bytes;
        if (transferredBytes - lastPublished >= BUFFER_SIZE || transferredBytes >= totalBytes) {
            lastPublished = transferredBytes;
            listener.update(transferredBytes, totalBytes);
        }
    }

    public void completed() {
        // transferredBytes == totalBytes ise tekrar publish etmesin
        if (transferredBytes < totalBytes) {
            transferredBytes = totalBytes;
            listener.update(totalBytes, totalBytes);
        }
    }
}
