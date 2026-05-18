package com.portal.auth.adapter.in.http;

import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.shared.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public final class MqttCommandConsumer {
    private final String host;
    private final int port;
    private final String registerTopic;
    private final String loginTopic;
    private final String issuePasswordTopic;
    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final IssuePasswordUseCase issuePasswordUseCase;

    public MqttCommandConsumer(String host,
                               int port,
                               String registerTopic,
                               String loginTopic,
                               String issuePasswordTopic,
                               RegisterUserUseCase registerUserUseCase,
                               LoginUseCase loginUseCase,
                               IssuePasswordUseCase issuePasswordUseCase) {
        this.host = host;
        this.port = port;
        this.registerTopic = registerTopic;
        this.loginTopic = loginTopic;
        this.issuePasswordTopic = issuePasswordTopic;
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.issuePasswordUseCase = issuePasswordUseCase;
    }

    public void start() {
        startTopicThread(registerTopic, this::handleRegister);
        startTopicThread(loginTopic, this::handleLogin);
        startTopicThread(issuePasswordTopic, this::handleIssuePassword);
    }

    private void startTopicThread(String topic, PayloadHandler handler) {
        Thread thread = new Thread(() -> consumeLoop(topic, handler), "mqtt-consumer-" + topic.replace('/', '-'));
        thread.setDaemon(true);
        thread.start();
    }

    private void consumeLoop(String topic, PayloadHandler handler) {
        while (true) {
            Process process = null;
            try {
                process = new ProcessBuilder(
                        "mosquitto_sub", "-h", host, "-p", String.valueOf(port), "-t", topic
                ).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        handler.handle(line);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                System.err.println("[" + Instant.now() + "] mqtt_consumer_failed topic=" + topic + " message=" + e.getMessage());
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    private void handleRegister(String payload) {
        try {
            Map<String, String> json = SimpleJson.parseFlatObject(payload);
            RegisterUserCommand command = new RegisterUserCommand(
                    json.get("template"),
                    json.get("firstName"),
                    json.get("lastName"),
                    parseInt(json.get("age")),
                    json.get("email"),
                    json.get("phone"),
                    json.get("mobile"),
                    json.get("address"),
                    json.get("socialFacebook"),
                    json.get("socialInstagram"),
                    json.get("socialTiktok"),
                    json.get("socialX"),
                    json.get("password")
            );
            registerUserUseCase.register(command);
        } catch (Exception e) {
            System.err.println("mqtt_register_payload_error: " + e.getMessage());
        }
    }

    private void handleLogin(String payload) {
        try {
            Map<String, String> json = SimpleJson.parseFlatObject(payload);
            loginUseCase.login(new LoginCommand(json.get("identifier"), json.get("password")));
        } catch (Exception e) {
            System.err.println("mqtt_login_payload_error: " + e.getMessage());
        }
    }

    private void handleIssuePassword(String payload) {
        try {
            Map<String, String> json = SimpleJson.parseFlatObject(payload);
            issuePasswordUseCase.issueTemporaryPassword(json.get("email"));
        } catch (Exception e) {
            System.err.println("mqtt_issue_password_payload_error: " + e.getMessage());
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    @FunctionalInterface
    private interface PayloadHandler {
        void handle(String payload) throws IOException;
    }
}
