package com.company.s3explorer.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ProgressOutputStream extends FilterOutputStream {
    private final ProgressTracker tracker;

    public ProgressOutputStream(OutputStream out, long totalBytes, TransferProgressListener listener) {
        super(out);
        tracker = new ProgressTracker(totalBytes, listener);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        tracker.transferred(len);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        tracker.transferred(1);
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