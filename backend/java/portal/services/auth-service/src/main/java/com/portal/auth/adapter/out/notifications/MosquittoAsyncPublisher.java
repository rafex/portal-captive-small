package com.portal.auth.adapter.out.notifications;

import com.portal.auth.application.port.out.AsyncEventPublisher;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class MosquittoAsyncPublisher implements AsyncEventPublisher {
    private final String mqttHost;
    private final int mqttPort;
    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();

    public MosquittoAsyncPublisher(String mqttHost, int mqttPort) {
        this.mqttHost = mqttHost;
        this.mqttPort = mqttPort;
        Thread worker = new Thread(this::processLoop, "mqtt-publisher-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void publish(String topic, String payload) {
        queue.offer(new Event(topic, payload));
    }

    private void processLoop() {
        while (true) {
            try {
                Event event = queue.take();
                publishViaMosquitto(event.topic(), event.payload());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("[" + Instant.now() + "] mqtt_publish_failed " + e.getMessage());
            }
        }
    }

    private void publishViaMosquitto(String topic, String payload) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "mosquitto_pub",
                "-h", mqttHost,
                "-p", String.valueOf(mqttPort),
                "-t", topic,
                "-m", payload
        );
        Process process = pb.start();
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("mosquitto_pub_exit=" + code + " topic=" + topic);
        }
    }

    private record Event(String topic, String payload) {
    }
}
