package com.portal.auth.adapter.in.http;

import com.portal.auth.adapter.out.mqtt.DbMqttMetrics;
import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.shared.SimpleJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

public final class AuthHttpHandler implements HttpHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final IssuePasswordUseCase issuePasswordUseCase;
    private final Supplier<Boolean> dbMqttHealthSupplier;

    public AuthHttpHandler(RegisterUserUseCase registerUserUseCase,
                           LoginUseCase loginUseCase,
                           IssuePasswordUseCase issuePasswordUseCase,
                           Supplier<Boolean> dbMqttHealthSupplier) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.issuePasswordUseCase = issuePasswordUseCase;
        this.dbMqttHealthSupplier = dbMqttHealthSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCors(exchange);
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            if ("/health".equals(path) && "GET".equals(method)) {
                writeJson(exchange, 200, "{\"status\":\"ok\"}");
                return;
            }
            if ("/health/db-mqtt".equals(path) && "GET".equals(method)) {
                boolean healthy = dbMqttHealthSupplier.get();
                writeJson(exchange, healthy ? 200 : 503,
                        "{\"component\":\"db_mqtt\",\"healthy\":" + healthy + "}");
                return;
            }
            if ("/metrics/db-mqtt".equals(path) && "GET".equals(method)) {
                writeJson(exchange, 200, DbMqttMetrics.asJson());
                return;
            }
            if ("/metrics/db-mqtt/prometheus".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/plain; version=0.0.4", DbMqttMetrics.asPrometheus());
                return;
            }
            if ("/auth/register".equals(path) && "POST".equals(method)) {
                register(exchange);
                return;
            }
            if ("/auth/login".equals(path) && "POST".equals(method)) {
                login(exchange);
                return;
            }
            if ("/auth/password/issue".equals(path) && "POST".equals(method)) {
                issuePassword(exchange);
                return;
            }
            writeJson(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (IllegalArgumentException e) {
            writeJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            writeJson(exchange, 500, "{\"error\":\"internal_error\"}");
        }
    }

    private void register(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> json = SimpleJson.parseFlatObject(body);
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
        writeJson(exchange, 201, "{\"userId\":\"" + userId + "\"}");
    }

    private void login(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> json = SimpleJson.parseFlatObject(body);
        LoginResult result = loginUseCase.login(new LoginCommand(json.get("identifier"), json.get("password")));
        int status = result.authenticated() ? 200 : 401;
        writeJson(exchange, status,
                "{\"authenticated\":" + result.authenticated() +
                        ",\"userId\":\"" + safe(result.userId()) +
                        "\",\"reason\":\"" + result.reason() + "\"}");
    }

    private void issuePassword(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> json = SimpleJson.parseFlatObject(body);
        issuePasswordUseCase.issueTemporaryPassword(json.get("email"));
        writeJson(exchange, 202, "{\"status\":\"queued\"}");
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        writeText(exchange, status, "application/json; charset=utf-8", body);
    }

    private static void writeText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
