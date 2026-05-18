package com.portal.auth.adapter.out.notifications;

import com.portal.auth.application.port.out.AsyncEventPublisher;

import java.time.Instant;

public final class StdoutAsyncPublisher implements AsyncEventPublisher {
    @Override
    public void publish(String topic, String payload) {
        System.out.println("[" + Instant.now() + "] topic=" + topic + " payload=" + payload);
    }
}
