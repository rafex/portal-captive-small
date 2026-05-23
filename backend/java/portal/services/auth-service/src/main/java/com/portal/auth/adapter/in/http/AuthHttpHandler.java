package com.portal.auth.adapter.in.http;

import com.portal.auth.adapter.out.mqtt.DbMqttMetrics;
import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.application.service.AuthService;
import com.portal.auth.shared.SimpleJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AuthHttpHandler implements HttpHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final IssuePasswordUseCase issuePasswordUseCase;
    private final Supplier<Boolean> dbMqttHealthSupplier;
    private final Supplier<String> portalIndexHtmlSupplier;
    private final Supplier<String> portalCoreJsSupplier;
    private final Supplier<String> portalStylesCssSupplier;
    private final Function<String, byte[]> assetBytesSupplier;
    private final String portalBootstrapJson;
    private final Set<String> allowedTemplates;
    private final AuthService authService;

    public AuthHttpHandler(RegisterUserUseCase registerUserUseCase,
                           LoginUseCase loginUseCase,
                           IssuePasswordUseCase issuePasswordUseCase,
                           Supplier<Boolean> dbMqttHealthSupplier,
                           Supplier<String> portalIndexHtmlSupplier,
                           Supplier<String> portalCoreJsSupplier,
                           Supplier<String> portalStylesCssSupplier,
                           Function<String, byte[]> assetBytesSupplier,
                           String portalBootstrapJson,
                           Set<String> allowedTemplates) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.issuePasswordUseCase = issuePasswordUseCase;
        this.dbMqttHealthSupplier = dbMqttHealthSupplier;
        this.portalIndexHtmlSupplier = portalIndexHtmlSupplier;
        this.portalCoreJsSupplier = portalCoreJsSupplier;
        this.portalStylesCssSupplier = portalStylesCssSupplier;
        this.assetBytesSupplier = assetBytesSupplier;
        this.portalBootstrapJson = portalBootstrapJson;
        this.allowedTemplates = allowedTemplates;
        this.authService = registerUserUseCase instanceof AuthService s ? s : null;
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
            if (path.startsWith("/portal/") && "GET".equals(method)) {
                String tmpl = path.substring("/portal/".length()).trim().toLowerCase();
                if (!tmpl.isEmpty() && allowedTemplates.contains(tmpl)) {
                    writeText(exchange, 200, "text/html; charset=utf-8", renderPortalHtml(tmpl));
                } else {
                    writeJson(exchange, 404, "{\"error\":\"template_not_found\"}");
                }
                return;
            }
            if ("/assets/styles.css".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/css; charset=utf-8", portalStylesCssSupplier.get());
                return;
            }
            if ("/assets/portal-core.js".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/javascript; charset=utf-8", portalCoreJsSupplier.get());
                return;
            }
            if ("/favicon.ico".equals(path) && "GET".equals(method)) {
                serveStaticAsset(exchange, "/assets/favicon.ico");
                return;
            }
            if (path.startsWith("/assets/") && "GET".equals(method)) {
                serveStaticAsset(exchange, path);
                return;
            }
            if ("/health".equals(path) && "GET".equals(method)) {
                writeJson(exchange, 200, "{\"status\":\"ok\"}");
                return;
            }
            if ("/auth/access/state".equals(path) && "GET".equals(method)) {
                accessState(exchange);
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
                requirePolicyConsent(exchange);
                register(exchange);
                return;
            }
            if ("/auth/login".equals(path) && "POST".equals(method)) {
                requirePolicyConsent(exchange);
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
                remoteIp(exchange),
                headerOrNull(exchange, "X-Device-UUID"),
                headerOrNull(exchange, "X-Device-Fingerprint"),
                headerOrNull(exchange, "X-Device-UA"),
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

    private void accessState(HttpExchange exchange) throws IOException {
        String ip = remoteIp(exchange);
        if (authService == null) {
            writeJson(exchange, 200, "{\"active\":false,\"remainingSeconds\":0}");
            return;
        }
        AuthService.AccessState state = authService.accessStateByDeviceIp(ip);
        writeJson(exchange, 200, "{\"active\":" + state.active() + ",\"remainingSeconds\":" + state.remainingSeconds() +
                ",\"userId\":\"" + safe(state.userId()) + "\"}");
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
        return portalIndexHtmlSupplier.get().replace("__PORTAL_BOOTSTRAP_JSON__", portalBootstrapJson);
    }

    private String renderPortalHtml(String templateOverride) {
        String overridden = portalBootstrapJson.replaceFirst(
                "\"selectedTemplate\":\"[^\"]*\"",
                "\"selectedTemplate\":\"" + templateOverride.replace("\"", "") + "\""
        );
        return portalIndexHtmlSupplier.get().replace("__PORTAL_BOOTSTRAP_JSON__", overridden);
    }

    private void serveStaticAsset(HttpExchange exchange, String path) throws IOException {
        byte[] bytes = assetBytesSupplier.apply(path);
        if (bytes == null || bytes.length == 0) {
            writeJson(exchange, 404, "{\"error\":\"asset_not_found\"}");
            return;
        }
        String contentType = guessContentType(path);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type,X-Terms-Accepted,X-Cookies-Accepted,X-Device-UUID,X-Device-Fingerprint,X-Device-UA,X-Forwarded-For"
        );
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

    private static String remoteIp(HttpExchange exchange) {
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "";
    }

    private static String headerOrNull(HttpExchange exchange, String name) {
        String v = exchange.getRequestHeaders().getFirst(name);
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }

    private static void requirePolicyConsent(HttpExchange exchange) {
        String terms = headerOrNull(exchange, "X-Terms-Accepted");
        String cookies = headerOrNull(exchange, "X-Cookies-Accepted");
        if (!"true".equalsIgnoreCase(terms) || !"true".equalsIgnoreCase(cookies)) {
            throw new IllegalArgumentException("policy_not_accepted");
        }
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
