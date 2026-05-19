package com.portal.auth.adapter.out.mqtt;

import com.portal.auth.shared.SimpleJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MqttCliRpcClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final String requestTopic;
    private final String responseTopicWildcard;

    private final Map<String, LinkedBlockingQueue<String>> inflight = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Process subProcess;
    private Process pubProcess;
    private BufferedWriter pubWriter;

    public MqttCliRpcClient(String host, int port, String requestTopic, String responseTopicWildcard) {
        this.host = host;
        this.port = port;
        this.requestTopic = requestTopic;
        this.responseTopicWildcard = responseTopicWildcard;
        startProcesses();
    }

    public String request(String requestId, String payload, long timeoutSeconds) {
        ensureRunning();
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(1);
        inflight.put(requestId, queue);
        try {
            publish(payload);
            String response = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
            if (response == null) {
                throw new IllegalStateException("mqtt_rpc_timeout requestId=" + requestId);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mqtt_rpc_interrupted", e);
        } finally {
            inflight.remove(requestId);
        }
    }

    public boolean isHealthy() {
        return running.get() && subProcess != null && subProcess.isAlive() && pubProcess != null && pubProcess.isAlive();
    }

    private synchronized void startProcesses() {
        try {
            subProcess = new ProcessBuilder(
                    "mosquitto_sub", "-h", host, "-p", String.valueOf(port), "-t", responseTopicWildcard
            ).start();

            pubProcess = new ProcessBuilder(
                    "mosquitto_pub", "-h", host, "-p", String.valueOf(port), "-t", requestTopic, "-l"
            ).start();
            pubWriter = new BufferedWriter(new OutputStreamWriter(pubProcess.getOutputStream(), StandardCharsets.UTF_8));
            running.set(true);

            Thread subThread = new Thread(this::readLoop, "mqtt-rpc-sub-loop");
            subThread.setDaemon(true);
            subThread.start();
        } catch (IOException e) {
            running.set(false);
            throw new IllegalStateException("mqtt_cli_start_failed", e);
        }
    }

    private void ensureRunning() {
        if (!isHealthy()) {
            restartProcesses();
        }
    }

    private synchronized void restartProcesses() {
        destroyProcess(subProcess);
        destroyProcess(pubProcess);
        running.set(false);
        startProcesses();
    }

    private void publish(String payload) {
        synchronized (writeLock) {
            try {
                pubWriter.write(payload);
                pubWriter.newLine();
                pubWriter.flush();
            } catch (IOException e) {
                restartProcesses();
                throw new IllegalStateException("mqtt_publish_failed", e);
            }
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(subProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && running.get()) {
                dispatch(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[" + Instant.now() + "] mqtt_rpc_sub_read_error " + e.getMessage());
            }
        }
    }

    private void dispatch(String payload) {
        try {
            Map<String, String> json = SimpleJson.parseFlatObject(payload);
            String requestId = json.get("requestId");
            if (requestId == null || requestId.isBlank()) {
                return;
            }
            LinkedBlockingQueue<String> queue = inflight.get(requestId);
            if (queue != null) {
                queue.offer(payload);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public synchronized void close() {
        running.set(false);
        destroyProcess(subProcess);
        destroyProcess(pubProcess);
        subProcess = null;
        pubProcess = null;
        pubWriter = null;
        inflight.clear();
    }

    private static void destroyProcess(Process process) {
        if (process != null) {
            process.destroy();
        }
    }
}
