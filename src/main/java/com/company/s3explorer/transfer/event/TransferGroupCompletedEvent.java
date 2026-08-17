package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.model.TransferGroup;

public class TransferGroupCompletedEvent {

    private final TransferGroup group;
    private final String repository;
    private final String bucket;
    private final String prefix;

    public TransferGroupCompletedEvent(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix) {

        this.group = group;
        this.repository = repository;
        this.bucket = bucket;
        this.prefix = prefix;
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

    public boolean isSuccessful() {
        return group.isFullySuccessful();
    }
}