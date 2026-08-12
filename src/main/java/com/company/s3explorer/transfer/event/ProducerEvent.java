package com.company.s3explorer.transfer.event;

import com.company.s3explorer.transfer.producer.ProducerRuntime;

public class ProducerEvent {

    private final ProducerRuntime runtime;

    public ProducerEvent(ProducerRuntime runtime) {
        this.runtime = runtime;
    }

    public ProducerRuntime getRuntime() {
        return runtime;
    }
}