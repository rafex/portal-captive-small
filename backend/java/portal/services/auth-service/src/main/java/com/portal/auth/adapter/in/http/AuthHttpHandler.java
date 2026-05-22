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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthHttpHandler implements HttpHandler {
    private static final Pattern TEMPLATE_SECTION_PATTERN = Pattern.compile("^\\s*\\[templates\\.([A-Za-z0-9_\\-]+)]\\s*$");
    private static final Set<String> DEFAULT_TEMPLATES = Set.of("hotel", "restaurante", "escuela", "casa", "personalizado");

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final IssuePasswordUseCase issuePasswordUseCase;
    private final Supplier<Boolean> dbMqttHealthSupplier;
    private final Supplier<String> portalTomlSupplier;
    private final Supplier<String> portalCoreJsSupplier;
    private final Set<String> supportedTemplates;
    private final String registrationTemplate;

    public AuthHttpHandler(RegisterUserUseCase registerUserUseCase,
                           LoginUseCase loginUseCase,
                           IssuePasswordUseCase issuePasswordUseCase,
                           Supplier<Boolean> dbMqttHealthSupplier,
                           Supplier<String> portalTomlSupplier,
                           Supplier<String> portalCoreJsSupplier,
                           String registrationTemplate) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.issuePasswordUseCase = issuePasswordUseCase;
        this.dbMqttHealthSupplier = dbMqttHealthSupplier;
        this.portalTomlSupplier = portalTomlSupplier;
        this.portalCoreJsSupplier = portalCoreJsSupplier;
        this.supportedTemplates = parseTemplatesFromToml(portalTomlSupplier.get());
        this.registrationTemplate = normalizeTemplate(registrationTemplate, supportedTemplates);
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
            if ("/".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/html; charset=utf-8", renderPortalHtml());
                return;
            }
            if ("/assets/portal-core.js".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/javascript; charset=utf-8", portalCoreJsSupplier.get());
                return;
            }
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
            if ("/portal/config/toml".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/plain; charset=utf-8", portalTomlSupplier.get());
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

    private String renderPortalHtml() {
        return """
                <!doctype html>
                <html lang="es">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Portal Cautivo</title>
                    <style>
                      body { font-family: sans-serif; margin: 0; background: #f5f7fb; color: #111; }
                      #app { max-width: 760px; margin: 0 auto; padding: 1.25rem; }
                      section { background: #fff; border: 1px solid #e5e7eb; border-radius: .75rem; padding: 1rem; margin-bottom: 1rem; }
                      h1,h2 { margin: 0 0 .75rem 0; }
                      form { display: grid; gap: .5rem; }
                      input,button { font: inherit; padding: .6rem .7rem; border-radius: .5rem; border: 1px solid #d1d5db; }
                      button { background: #0f766e; color: #fff; border-color: #0f766e; cursor: pointer; }
                      #status { min-height: 1.25rem; }
                    </style>
                  </head>
                  <body>
                    <div id="app"></div>
                    <script type="module">
                      import { mountPortal } from '/assets/portal-core.js';
                      mountPortal(document.getElementById('app'), {
                        apiBaseUrl: '',
                        template: '%s'
                      });
                    </script>
                  </body>
                </html>
                """.formatted(registrationTemplate);
    }

    private static String normalizeTemplate(String raw, Set<String> supportedTemplates) {
        if (raw == null) {
            return "hotel";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return supportedTemplates.contains(v) ? v : "hotel";
    }

    private static Set<String> parseTemplatesFromToml(String toml) {
        Set<String> out = new LinkedHashSet<>();
        if (toml != null) {
            String[] lines = toml.split("\\R");
            for (String line : lines) {
                Matcher m = TEMPLATE_SECTION_PATTERN.matcher(line);
                if (m.matches()) {
                    out.add(m.group(1).trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(DEFAULT_TEMPLATES);
        }
        return out;
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
