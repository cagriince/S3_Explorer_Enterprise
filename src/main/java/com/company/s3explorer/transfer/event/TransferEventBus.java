package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.TransferRuntime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TransferEventBus {

    private final List<TransferListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(TransferListener listener) {
        listeners.add(listener);
    }

    public void publish(TransferRuntime runtime) {
        for (TransferListener l : listeners) {
            l.onTransferUpdated(runtime);
        }
    }
}
