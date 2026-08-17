package com.company.s3explorer.transfer.context;

import com.company.s3explorer.service.S3ExplorerService;
import com.company.s3explorer.service.S3ClientManager;
import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferEventBus;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

import java.time.Instant;

public class TransferContext {

    private final S3ClientManager clientManager;
    private final TransferEventBus eventBus;

    public TransferContext(
            S3ClientManager clientManager,
            TransferEventBus eventBus) {

        this.clientManager = clientManager;
        this.eventBus = eventBus;
    }

    public S3ExplorerService getService(
            String repositoryName) {

        return new S3ExplorerService(
                clientManager.getClient(repositoryName));
    }

    public void publish(TransferRuntime runtime) {
        eventBus.publish(runtime);
    }

    public void publishRunning(TransferRuntime runtime) {
        runtime.setStartTime(Instant.now());
        runtime.setStatus(TransferStatus.RUNNING);
        publish(runtime);
    }

    public void publishCompleted(TransferRuntime runtime) {
        runtime.setEndTime(Instant.now());
        runtime.progressCompleted();
        runtime.setStatus(TransferStatus.COMPLETED);
        publish(runtime);
    }

    public void publishCancelled(TransferRuntime runtime) {
        runtime.setEndTime(Instant.now());
        runtime.setMessage("Transfer cancelled");
        runtime.setStatus(TransferStatus.CANCELLED);
        publish(runtime);
    }

    public void publishFailed(
            TransferRuntime runtime,
            Exception ex) {

        runtime.setEndTime(Instant.now());
        runtime.setMessage(ex.getMessage());
        runtime.setStatus(TransferStatus.FAILED);
        publish(runtime);
    }

    public void publishProgress(TransferRuntime runtime) {
        eventBus.publish(runtime);
    }

    public void publishProducer(
            ProducerRuntime runtime) {

        eventBus.publishProducer(runtime);
    }

    public void publishGroupCompleted(
            TransferGroup group,
            String repository,
            String bucket,
            String prefix) {

        eventBus.publishGroupCompleted(
                group,
                repository,
                bucket,
                prefix);
    }
}