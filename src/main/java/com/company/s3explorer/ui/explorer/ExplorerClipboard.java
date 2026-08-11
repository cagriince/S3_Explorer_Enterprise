package com.company.s3explorer.ui.explorer;

import java.util.List;

public class ExplorerClipboard {

    public enum Operation {
        COPY,
        MOVE
    }

    private Operation operation;

    private List<S3FileItem> items = List.of();

    public void copy(List<S3FileItem> items) {
        this.operation = Operation.COPY;
        this.items = List.copyOf(items);
    }

    public void move(List<S3FileItem> items) {
        this.operation = Operation.MOVE;
        this.items = List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Operation getOperation() {
        return operation;
    }

    public List<S3FileItem> getItems() {
        return items;
    }

    public void clear() {
        items = List.of();
        operation = null;
    }
}
