package com.portal.auth.adapter.in.http;

import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.shared.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MqttCommandConsumer {
    private final String host;
    private final int port;
    private final String registerTopic;
    private final String loginTopic;
    private final String issuePasswordTopic;
    private final String registerOutTopic;
    private final String loginOutTopic;
    private final String issuePasswordOutTopic;
    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final IssuePasswordUseCase issuePasswordUseCase;
    private final AsyncEventPublisher asyncEventPublisher;

    public MqttCommandConsumer(String host,
                               int port,
                               String registerTopic,
                               String loginTopic,
                               String issuePasswordTopic,
                               String registerOutTopic,
                               String loginOutTopic,
                               String issuePasswordOutTopic,
                               RegisterUserUseCase registerUserUseCase,
                               LoginUseCase loginUseCase,
                               IssuePasswordUseCase issuePasswordUseCase,
                               AsyncEventPublisher asyncEventPublisher) {
        this.host = host;
        this.port = port;
        this.registerTopic = registerTopic;
        this.loginTopic = loginTopic;
        this.issuePasswordTopic = issuePasswordTopic;
        this.registerOutTopic = registerOutTopic;
        this.loginOutTopic = loginOutTopic;
        this.issuePasswordOutTopic = issuePasswordOutTopic;
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.issuePasswordUseCase = issuePasswordUseCase;
        this.asyncEventPublisher = asyncEventPublisher;
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
        Map<String, String> json = safeParse(payload, registerOutTopic);
        if (json == null) {
            return;
        }
        String requestId = requestId(json);

        try {
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
            String userId = registerUserUseCase.register(command);
            publishOk(registerOutTopic, requestId, "{\"userId\":\"" + userId + "\"}");
        } catch (Exception e) {
            publishError(registerOutTopic, requestId, e.getMessage());
        }
    }

    private void handleLogin(String payload) {
        Map<String, String> json = safeParse(payload, loginOutTopic);
        if (json == null) {
            return;
        }
        String requestId = requestId(json);

        try {
            LoginResult result = loginUseCase.login(new LoginCommand(json.get("identifier"), json.get("password")));
            String data = "{\"authenticated\":" + result.authenticated() +
                    ",\"userId\":\"" + safe(result.userId()) +
                    "\",\"reason\":\"" + safe(result.reason()) + "\"}";
            publishOk(loginOutTopic, requestId, data);
        } catch (Exception e) {
            publishError(loginOutTopic, requestId, e.getMessage());
        }
    }

    private void handleIssuePassword(String payload) {
        Map<String, String> json = safeParse(payload, issuePasswordOutTopic);
        if (json == null) {
            return;
        }
        String requestId = requestId(json);

        try {
            issuePasswordUseCase.issueTemporaryPassword(json.get("email"));
            publishOk(issuePasswordOutTopic, requestId, "{\"status\":\"queued\"}");
        } catch (Exception e) {
            publishError(issuePasswordOutTopic, requestId, e.getMessage());
        }
    }

    private Map<String, String> safeParse(String payload, String outTopic) {
        try {
            return SimpleJson.parseFlatObject(payload);
        } catch (Exception e) {
            publishError(outTopic, UUID.randomUUID().toString(), "invalid_json");
            return null;
        }
    }

    private String requestId(Map<String, String> json) {
        String value = json.get("requestId");
        return (value == null || value.isBlank()) ? UUID.randomUUID().toString() : value;
    }

    private void publishOk(String topic, String requestId, String dataJson) {
        asyncEventPublisher.publish(topic,
                "{\"requestId\":\"" + safe(requestId) + "\",\"status\":\"ok\",\"data\":" + dataJson + "}");
    }

    private void publishError(String topic, String requestId, String message) {
        asyncEventPublisher.publish(topic,
                "{\"requestId\":\"" + safe(requestId) + "\",\"status\":\"error\",\"error\":\"" + safe(message) + "\"}");
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'");
    }

    @FunctionalInterface
    private interface PayloadHandler {
        void handle(String payload) throws IOException;
    }
}
