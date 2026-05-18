package com.portal.auth.application.port.out;

public interface AsyncEventPublisher {
    void publish(String topic, String payload);
}
