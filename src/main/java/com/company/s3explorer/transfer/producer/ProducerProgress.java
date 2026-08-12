package com.company.s3explorer.transfer.producer;

import java.util.function.LongConsumer;

public class ProducerProgress {

    private final LongConsumer discoveredConsumer;

    private long discoveredCount;

    public ProducerProgress(LongConsumer discoveredConsumer) {
        this.discoveredConsumer = discoveredConsumer;
    }

    public void discovered() {
        discoveredCount++;
        discoveredConsumer.accept(discoveredCount);
    }

    public long getDiscoveredCount() {
        return discoveredCount;
    }
}