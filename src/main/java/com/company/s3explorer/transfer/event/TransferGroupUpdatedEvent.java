package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.model.TransferGroup;

public class TransferGroupUpdatedEvent {

    private final TransferGroup group;

    public TransferGroupUpdatedEvent(
            TransferGroup group) {

        this.group = group;
    }

    public TransferGroup getGroup() {
        return group;
    }

    public boolean isFinished() {

        return group.isFinished();
    }
}