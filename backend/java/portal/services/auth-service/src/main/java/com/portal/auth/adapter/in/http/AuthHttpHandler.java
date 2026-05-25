package com.portal.auth.adapter.in.http;

import com.portal.auth.adapter.out.mqtt.DbMqttMetrics;
import com.portal.auth.adapter.out.notifications.Sha256PasswordHasher;
import com.portal.auth.adapter.out.sqlite.AdminSqliteCliRepository;
import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.application.service.AdminSessionService;
import com.portal.auth.application.service.AuthService;
import com.portal.auth.shared.SimpleJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuthHttpHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(AuthHttpHandler.class.getName());
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
    private final AdminSqliteCliRepository adminRepository;
    private final AdminSessionService adminSessionService;
    private final Sha256PasswordHasher adminPasswordHasher;

    public AuthHttpHandler(RegisterUserUseCase registerUserUseCase,
                           LoginUseCase loginUseCase,
                           IssuePasswordUseCase issuePasswordUseCase,
                           Supplier<Boolean> dbMqttHealthSupplier,
                           Supplier<String> portalIndexHtmlSupplier,
                           Supplier<String> portalCoreJsSupplier,
                           Supplier<String> portalStylesCssSupplier,
                           Function<String, byte[]> assetBytesSupplier,
                           String portalBootstrapJson,
                           Set<String> allowedTemplates,
                           AdminSqliteCliRepository adminRepository,
                           AdminSessionService adminSessionService) {
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
        this.adminRepository = adminRepository;
        this.adminSessionService = adminSessionService;
        this.adminPasswordHasher = new Sha256PasswordHasher();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCors(exchange);
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String rid = requestId(exchange);

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
            if ("/admin".equals(path) && "GET".equals(method)) {
                writeText(exchange, 200, "text/html; charset=utf-8", renderAdminHtml());
                return;
            }
            if ("/admin/login".equals(path) && "POST".equals(method)) {
                adminLogin(exchange);
                return;
            }
            if ("/admin/users".equals(path) && "POST".equals(method)) {
                AdminSessionService.Session session = requireAdminSession(exchange, true);
                adminCreateUser(exchange, session);
                return;
            }
            if ("/admin/users/registered".equals(path) && "GET".equals(method)) {
                requireAdminSession(exchange, false);
                adminListRegistered(exchange);
                return;
            }
            writeJson(exchange, 404, "{\"error\":\"not_found\"}");
        } catch (IllegalArgumentException e) {
            LOGGER.info("http_request_failed rid=" + rid + " method=" + method + " path=" + path + " status=400 error=" + safe(e.getMessage()));
            writeJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        } catch (IllegalStateException e) {
            String message = safe(e.getMessage());
            if (message.contains("db_mqtt_rpc_exhausted") || message.contains("db_read_failed") || message.contains("db_write_failed")) {
                LOGGER.log(Level.WARNING, "http_request_failed rid=" + rid + " method=" + method + " path=" + path + " status=503 error=db_unavailable", e);
                writeJson(exchange, 503, "{\"error\":\"db_unavailable\"}");
                return;
            }
            LOGGER.log(Level.SEVERE, "http_request_failed rid=" + rid + " method=" + method + " path=" + path + " status=500", e);
            writeJson(exchange, 500, "{\"error\":\"internal_error\"}");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "http_request_failed rid=" + rid + " method=" + method + " path=" + path + " status=500", e);
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
                firstNonBlank(json.get("address"), json.get("room")),
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
        try {
            AuthService.AccessState state = authService.accessStateByDeviceIp(ip);
            writeJson(exchange, 200, "{\"active\":" + state.active() + ",\"remainingSeconds\":" + state.remainingSeconds() +
                    ",\"userId\":\"" + safe(state.userId()) + "\"}");
        } catch (RuntimeException e) {
            LOGGER.warning("access_state_failed ip=" + ip + " error=" + safe(e.getMessage()));
            writeJson(exchange, 200, "{\"active\":false,\"remainingSeconds\":0}");
        }
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

    private void adminLogin(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> json = SimpleJson.parseFlatObject(body);
        String username = normalize(json.get("username"));
        String password = json.get("password");
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("admin_credentials_required");
        }
        var userOpt = adminRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            writeJson(exchange, 401, "{\"error\":\"invalid_admin_credentials\"}");
            return;
        }
        var user = userOpt.get();
        if (!user.enabled()) {
            writeJson(exchange, 403, "{\"error\":\"admin_user_disabled\"}");
            return;
        }
        String actual = adminPasswordHasher.hash(password, user.passwordSalt());
        if (!actual.equals(user.passwordHash())) {
            writeJson(exchange, 401, "{\"error\":\"invalid_admin_credentials\"}");
            return;
        }
        String token = adminSessionService.issue(user.username(), user.role());
        writeJson(exchange, 200, "{\"token\":\"" + safe(token) + "\",\"role\":\"" + safe(user.role()) + "\"}");
    }

    private void adminCreateUser(HttpExchange exchange, AdminSessionService.Session session) throws IOException {
        if (!session.isAdmin()) {
            writeJson(exchange, 403, "{\"error\":\"admin_role_required\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> json = SimpleJson.parseFlatObject(body);
        String username = normalize(json.get("username"));
        String password = json.get("password");
        String role = normalize(json.get("role"));
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("admin_credentials_required");
        }
        if (!"admin".equals(role) && !"viewer".equals(role)) {
            throw new IllegalArgumentException("invalid_admin_role");
        }
        String salt = adminPasswordHasher.salt();
        String hash = adminPasswordHasher.hash(password, salt);
        adminRepository.saveAdmin(username, hash, salt, role, true);
        writeJson(exchange, 201, "{\"status\":\"ok\",\"username\":\"" + safe(username) + "\",\"role\":\"" + role + "\"}");
    }

    private void adminListRegistered(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int limit = parseLimit(query);
        var rows = adminRepository.listRegisteredUsers(limit);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(rows.size()).append(",\"users\":[");
        boolean first = true;
        for (var r : rows) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"userId\":\"").append(safe(r.userId())).append("\",");
            sb.append("\"createdAt\":\"").append(safe(r.createdAt())).append("\",");
            sb.append("\"updatedAt\":\"").append(safe(r.updatedAt())).append("\",");
            sb.append("\"profile\":").append((r.profileJson() == null || r.profileJson().isBlank()) ? "{}" : r.profileJson());
            sb.append("}");
        }
        sb.append("]}");
        writeJson(exchange, 200, sb.toString());
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

    private String renderAdminHtml() {
        return """
                <!doctype html>
                <html lang="es">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Portal Admin</title>
                    <link rel="stylesheet" href="/assets/styles.css" />
                  </head>
                  <body>
                    <div id="admin-app"></div>
                    <script type="module" src="/assets/admin.js"></script>
                  </body>
                </html>
                """;
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
                "Content-Type,Authorization,X-Request-Id,X-Terms-Accepted,X-Cookies-Accepted,X-Device-UUID,X-Device-Fingerprint,X-Device-UA,X-Forwarded-For"
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

    private AdminSessionService.Session requireAdminSession(HttpExchange exchange, boolean requireEnabled) {
        String token = bearerToken(exchange);
        var sessionOpt = adminSessionService.resolve(token);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("admin_unauthorized");
        }
        var session = sessionOpt.get();
        if (requireEnabled && !session.isAdmin() && !"viewer".equalsIgnoreCase(session.role())) {
            throw new IllegalArgumentException("admin_forbidden");
        }
        return session;
    }

    private static String bearerToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || auth.isBlank()) return null;
        if (!auth.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return auth.substring(7).trim();
    }

    private static int parseLimit(String query) {
        if (query == null || query.isBlank()) return 100;
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = part.substring(0, idx);
            String v = part.substring(idx + 1);
            if ("limit".equalsIgnoreCase(k)) {
                try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException ignored) {
                    return 100;
                }
            }
        }
        return 100;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        return v.isBlank() ? null : v;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requestId(HttpExchange exchange) {
        String existing = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
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
