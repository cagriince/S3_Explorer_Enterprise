package com.company.s3explorer.transfer.producer;

public class ProducerCancelledException
        extends RuntimeException {

    public ProducerCancelledException() {
        super("Producer cancelled");
    }
}