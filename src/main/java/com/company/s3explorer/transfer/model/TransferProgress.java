package com.company.s3explorer.transfer.model;

import java.time.Duration;

public class TransferProgress {
    private long transferredBytes;
    private long totalBytes;

    public void update(long transferred, long total) {
        this.transferredBytes = transferred;
        this.totalBytes = total;
    }

    public int getPercent() {
        if (totalBytes == 0) {
            return 0;
        }
        return (int)((transferredBytes * 100) / totalBytes);
    }

    public void complete() {
        update(totalBytes, totalBytes);
    }
}
