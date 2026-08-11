package com.company.s3explorer.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends FilterInputStream {
    private final ProgressTracker tracker;

    public ProgressInputStream(InputStream in, long totalBytes, TransferProgressListener listener) {
        super(in);
        tracker = new ProgressTracker(totalBytes, listener);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int count = super.read(b, off, len);
        if (count > 0) {
            tracker.transferred(count);
            /*try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
        }

        return count;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value != -1) {
            tracker.transferred(1);
        }

        return value;
    }

    @Override
    public void close() throws IOException {
        try {
            tracker.completed();
        } finally {
            super.close();
        }
    }
}