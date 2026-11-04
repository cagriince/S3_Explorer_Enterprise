
package com.company.s3explorer.transfer.logging;

import com.company.s3explorer.transfer.TransferRuntime;
import com.company.s3explorer.transfer.TransferStatus;
import com.company.s3explorer.transfer.event.TransferGroupCompletedEvent;
import com.company.s3explorer.transfer.event.TransferListener;
import com.company.s3explorer.transfer.model.TransferGroup;
import com.company.s3explorer.transfer.producer.ProducerRuntime;

import java.util.List;

public class TransferLogListener implements TransferListener {

    private final TransferLogWriter logWriter;

    public TransferLogListener(TransferLogWriter logWriter) {

        if (logWriter == null) {
            throw new IllegalArgumentException(
                    "TransferLogWriter cannot be null.");
        }

        this.logWriter = logWriter;
    }

    @Override
    public void onTransferUpdated(TransferRuntime runtime) {

        if (runtime == null) {
            return;
        }

        TransferStatus status = runtime.getStatus();

        if (status == null) {
            return;
        }

        if (status == TransferStatus.QUEUED) {
            logWriter.register(runtime);
            return;
        }

        if (status.isFinished()) {
            logWriter.record(runtime);
        }
    }

    @Override
    public void onQueuedTransfersCancelled(
            List<TransferRuntime> runtimes) {

        logWriter.recordQueuedCancelled(runtimes);
    }

    @Override
    public void onProducerUpdated(ProducerRuntime runtime) {
        // Producer lifecycle events are not file task results.
    }

    @Override
    public void onTransferGroupUpdated(TransferGroup group) {
        logWriter.registerGroup(group);
    }

    @Override
    public void onTransferGroupCompleted(
            TransferGroupCompletedEvent event) {

        if (event == null) {
            return;
        }

        logWriter.recordGroupCompleted(
                event.getGroup());
    }
}
