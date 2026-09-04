package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.model.TransferGroup;

public class TransferGroupUpdatedEvent {

    private final TransferGroup group;

    private final String repository;
    private final String bucket;
    private final String prefix;

    private final boolean sourceRefreshRequired;

    public TransferGroupUpdatedEvent(
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

    public boolean isPreparing() {

        return !group.isProductionCompleted()
                && group.getActiveProducers() > 0;
    }

    public boolean isRunning() {

        return !group.isFinished()
                && (
                group.getQueued() > 0
                        || group.getRunning() > 0
                        || group.getCompleted() > 0
                        || group.getFailed() > 0
                        || group.getCancelled() > 0
        );
    }

    public boolean isFinished() {

        return group.isFinished();
    }

    public boolean isSuccessful() {

        return group.isFullySuccessful();
    }
}