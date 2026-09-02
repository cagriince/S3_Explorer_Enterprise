package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.model.TransferGroup;

public class TransferGroupCompletedEvent {

    private final TransferGroup group;
    private final String repository;
    private final String bucket;
    private final String prefix;
    private final boolean sourceRefreshRequired;

    public TransferGroupCompletedEvent(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix,
            boolean sourceRefreshRequired) {

        this.group = group;
        this.repository = repository;
        this.bucket = bucket;
        this.prefix = prefix;
        this.sourceRefreshRequired =
                sourceRefreshRequired;
    }

    public TransferGroup getGroup() {
        return group;
    }

    public String getRepository() {
        return repository;
    }

    public String getBucket() {
        return bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isSourceRefreshRequired() {
        return sourceRefreshRequired;
    }

    public boolean isSuccessful() {
        return group.isFullySuccessful();
    }
}