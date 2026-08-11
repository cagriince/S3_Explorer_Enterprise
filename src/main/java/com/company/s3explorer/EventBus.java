package com.company.s3explorer;

import java.util.function.Consumer;

public interface EventBus {
    void publish(Object event);

    void subscribe(Class<?> eventType, Consumer<?> consumer);
}