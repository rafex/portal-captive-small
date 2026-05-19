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

public final class MqttCliRpcClient {
    private final String host;
    private final int port;
    private final String requestTopic;
    private final String responseTopicWildcard;

    private final Map<String, LinkedBlockingQueue<String>> inflight = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

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

    private void startProcesses() {
        try {
            subProcess = new ProcessBuilder(
                    "mosquitto_sub", "-h", host, "-p", String.valueOf(port), "-t", responseTopicWildcard
            ).start();

            pubProcess = new ProcessBuilder(
                    "mosquitto_pub", "-h", host, "-p", String.valueOf(port), "-t", requestTopic, "-l"
            ).start();
            pubWriter = new BufferedWriter(new OutputStreamWriter(pubProcess.getOutputStream(), StandardCharsets.UTF_8));

            Thread subThread = new Thread(this::readLoop, "mqtt-rpc-sub-loop");
            subThread.setDaemon(true);
            subThread.start();
        } catch (IOException e) {
            throw new IllegalStateException("mqtt_cli_start_failed", e);
        }
    }

    private void ensureRunning() {
        if (subProcess == null || !subProcess.isAlive() || pubProcess == null || !pubProcess.isAlive()) {
            restartProcesses();
        }
    }

    private void restartProcesses() {
        destroyProcess(subProcess);
        destroyProcess(pubProcess);
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
            while ((line = reader.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            System.err.println("[" + Instant.now() + "] mqtt_rpc_sub_read_error " + e.getMessage());
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

    private static void destroyProcess(Process process) {
        if (process != null) {
            process.destroy();
        }
    }
}
