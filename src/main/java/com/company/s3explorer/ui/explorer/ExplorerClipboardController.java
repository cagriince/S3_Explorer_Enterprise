package com.company.s3explorer.ui.explorer;

import java.util.ArrayList;
import java.util.List;

public final class ExplorerClipboardController {

    public enum Operation {
        COPY,
        MOVE
    }

    private final ExplorerClipboard clipboard;

    public ExplorerClipboardController(
            ExplorerClipboard clipboard) {

        this.clipboard = clipboard;
    }

    public void copy(
            List<S3FileItem> items) {

        if (items == null || items.isEmpty()) {
            return;
        }

        clipboard.copy(
                new ArrayList<>(items));
    }

    public void move(
            List<S3FileItem> items) {

        if (items == null || items.isEmpty()) {
            return;
        }

        clipboard.move(
                new ArrayList<>(items));
    }

    public boolean isEmpty() {
        return clipboard.isEmpty();
    }

    public List<S3FileItem> getItems() {
        return clipboard.getItems();
    }

    public ExplorerClipboard.Operation getOperation() {
        return clipboard.getOperation();
    }

    public void clear() {
        clipboard.clear();
    }
}