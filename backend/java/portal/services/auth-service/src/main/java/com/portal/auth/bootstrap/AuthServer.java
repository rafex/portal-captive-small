package com.portal.auth.bootstrap;

import com.portal.auth.adapter.in.http.AuthHttpHandler;
import com.portal.auth.adapter.in.http.MqttCommandConsumer;
import com.portal.auth.adapter.out.memory.InMemoryUserRepository;
import com.portal.auth.adapter.out.mqtt.MqttRustUserRepository;
import com.portal.auth.adapter.out.network.SshOpenWrtAccessGateway;
import com.portal.auth.adapter.out.notifications.MosquittoAsyncPublisher;
import com.portal.auth.adapter.out.notifications.Sha256PasswordHasher;
import com.portal.auth.adapter.out.notifications.SmtpSocketEmailSender;
import com.portal.auth.adapter.out.sqlite.SqliteCliUserRepository;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.application.port.out.OpenWrtAccessGateway;
import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.application.service.AuthService;
import com.portal.auth.application.service.RegistrationTemplatePolicy;
import com.portal.auth.config.PortalConfig;
import com.portal.auth.shared.SimpleToml;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthServer {
    private AuthServer() {
    }

    public static void run(Path configPath) throws IOException {
        PortalConfig config = PortalConfig.fromToml(configPath);
        AsyncEventPublisher publisher = new MosquittoAsyncPublisher(config.mqttHost(), config.mqttPort());
        UserRepository repository = buildRepository(config);
        OpenWrtAccessGateway openWrtGateway = new SshOpenWrtAccessGateway(
                config.openWrtHost(),
                config.openWrtPort(),
                config.openWrtUser(),
                config.openWrtEnabled()
        );

        AuthService service = new AuthService(
                repository,
                new Sha256PasswordHasher(),
                publisher,
                new SmtpSocketEmailSender(config.smtpHost(), config.smtpPort(), config.smtpFrom()),
                openWrtGateway,
                config.sessionTtlSeconds(),
                RegistrationTemplatePolicy.fromConfig(configPath)
        );

        MqttCommandConsumer commandConsumer = new MqttCommandConsumer(
                config.mqttHost(),
                config.mqttPort(),
                config.mqttTopicRegister(),
                config.mqttTopicLogin(),
                config.mqttTopicIssuePassword(),
                config.mqttTopicRegisterOut(),
                config.mqttTopicLoginOut(),
                config.mqttTopicIssuePasswordOut(),
                service,
                service,
                service,
                publisher
        );
        commandConsumer.start();

        Supplier<Boolean> dbMqttHealth = () -> !(repository instanceof MqttRustUserRepository r) || r.isHealthy();
        PortalBootstrap bootstrap = buildPortalBootstrap(configPath, config.registrationTemplate());
        Supplier<String> portalIndexHtml = resourceSupplier("portal/index.html",
                "<!doctype html><html><body><div id='app'></div><script>window.__PORTAL_CONFIG__=__PORTAL_BOOTSTRAP_JSON__;</script><script type='module' src='/assets/portal-core.js'></script></body></html>");
        Supplier<String> portalCoreJs = resourceSupplier("portal/portal-core.js",
                "export function mountPortal(root){ root.innerHTML='<p>portal-core.js no disponible</p>'; }");
        Supplier<String> portalStylesCss = resourceSupplier("portal/styles.css",
                "body{font-family:sans-serif} #app{max-width:760px;margin:0 auto;padding:1rem}");
        Function<String, byte[]> portalAssetBytes = resourceBytesSupplier("portal");

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(config.httpPort()), 0);
        httpServer.createContext("/", new AuthHttpHandler(
                service,
                service,
                service,
                dbMqttHealth,
                portalIndexHtml,
                portalCoreJs,
                portalStylesCss,
                portalAssetBytes,
                bootstrap.json(),
                bootstrap.templates()
        ));
        httpServer.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (repository instanceof MqttRustUserRepository r) {
                r.close();
            }
        }, "auth-shutdown-hook"));

        System.out.println("auth-service listening on :" + config.httpPort());
        System.out.println("auth-service repository=" + config.userRepositoryType());
    }

    private static UserRepository buildRepository(PortalConfig config) throws IOException {
        if ("memory".equalsIgnoreCase(config.userRepositoryType())) {
            return new InMemoryUserRepository();
        }
        if ("mqtt_rust".equalsIgnoreCase(config.userRepositoryType())) {
            return new MqttRustUserRepository(
                    config.mqttHost(),
                    config.mqttPort(),
                    config.dbMqttUserRequestTopic(),
                    config.dbMqttResponseWaitSeconds(),
                    config.dbMqttMaxRetries(),
                    config.dbMqttRetryBackoffMs()
            );
        }
        Path db = Path.of(config.sqliteDbPath());
        Path parent = db.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new SqliteCliUserRepository(db.toString());
    }

    private static Supplier<String> resourceSupplier(String resourcePath, String fallback) {
        return () -> {
            try (var in = AuthServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return fallback;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return fallback;
            }
        };
    }

    private static Function<String, byte[]> resourceBytesSupplier(String baseResourceDir) {
        return (path) -> {
            String normalized = path == null ? "" : path.trim();
            if (!normalized.startsWith("/assets/")) {
                return null;
            }
            String rel = normalized.substring("/assets/".length());
            String resourcePath = baseResourceDir + "/assets/" + rel;
            try (var in = AuthServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    return null;
                }
                return in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        };
    }

    private static PortalBootstrap buildPortalBootstrap(Path portalConfigPath, String fallbackTemplate) {
        Path repoRoot = resolveProjectRoot(portalConfigPath.toAbsolutePath());
        Map<String, String> portalCfg = SimpleToml.parseFlat(portalConfigPath);
        String portalRaw = readFile(portalConfigPath);
        String templatesCfgFile = parseKeyInSection(portalRaw, "templates_config", "file");
        if (templatesCfgFile == null || templatesCfgFile.isBlank()) {
            templatesCfgFile = portalCfg.getOrDefault("templates_config.file", "config/templates-config.toml");
        }
        String selectedTemplate = portalCfg.getOrDefault("registration.template", fallbackTemplate);
        Path templatesCfgPath = repoRoot.resolve(templatesCfgFile).normalize();
        String templatesCfgRaw = readFile(templatesCfgPath);
        Map<String, String> templatesCfg = SimpleToml.parseFlat(templatesCfgPath);
        String templatesDir = parseKeyInSection(templatesCfgRaw, "templates", "directory");
        if (templatesDir == null || templatesDir.isBlank()) {
            templatesDir = templatesCfg.getOrDefault("templates.directory", "config/templates");
        }
        Path templatesDirPath = repoRoot.resolve(templatesDir).normalize();

        List<String> available = parseArrayInSection(templatesCfgRaw, "templates", "available");
        if (available.isEmpty()) {
            available = parseStringArray(templatesCfg.getOrDefault("templates.available", ""));
        }
        if (available.isEmpty()) {
            available = listTemplateFiles(templatesDirPath);
        }
        Map<String, TemplateData> templates = new LinkedHashMap<>();
        for (String name : available) {
            Path f = templatesDirPath.resolve(name + ".toml");
            templates.put(name, readTemplateData(f, name));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"selectedTemplate\":\"").append(escape(selectedTemplate)).append("\",");
        sb.append("\"templates\":{");
        boolean firstTemplate = true;
        for (Map.Entry<String, TemplateData> e : templates.entrySet()) {
            if (!firstTemplate) sb.append(",");
            firstTemplate = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append(e.getValue().toJson());
        }
        sb.append("}}");
        return new PortalBootstrap(sb.toString(), new LinkedHashSet<>(available), selectedTemplate);
    }

    private static Path resolveProjectRoot(Path portalConfigPath) {
        Path p = portalConfigPath.getParent();
        if (p == null) {
            return Path.of(".").toAbsolutePath();
        }
        if ("config".equals(p.getFileName() != null ? p.getFileName().toString() : "")) {
            Path maybeBackend = p.getParent();
            if (maybeBackend != null && "backend".equals(maybeBackend.getFileName() != null ? maybeBackend.getFileName().toString() : "")) {
                Path root = maybeBackend.getParent();
                if (root != null) {
                    return root;
                }
            }
        }
        Path parent = p.getParent();
        return parent != null ? parent : Path.of(".").toAbsolutePath();
    }

    private static TemplateData readTemplateData(Path path, String fallbackName) {
        Map<String, String> kv = SimpleToml.parseFlat(path);
        String raw = readFile(path);
        List<FieldRule> fields = parseFieldsEnabled(raw);
        return new TemplateData(
                kv.getOrDefault("template.title", fallbackName),
                kv.getOrDefault("template.logo", "/assets/logo.png"),
                kv.getOrDefault("template.background", "/assets/bg.jpg"),
                kv.getOrDefault("template.primary_color", "#0f766e"),
                fields
        );
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static List<String> parseStringArray(String raw) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(raw == null ? "" : raw);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String parseKeyInSection(String toml, String section, String key) {
        if (toml == null || toml.isBlank()) {
            return null;
        }
        boolean inSection = false;
        for (String rawLine : toml.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                String sec = line.substring(1, line.length() - 1).trim();
                inSection = section.equals(sec);
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith(key + " ")) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    return null;
                }
                String value = line.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static List<String> parseArrayInSection(String toml, String section, String key) {
        List<String> out = new ArrayList<>();
        if (toml == null || toml.isBlank()) {
            return out;
        }
        boolean inSection = false;
        boolean inArray = false;
        StringBuilder buf = new StringBuilder();
        for (String rawLine : toml.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!inArray && line.startsWith("[") && line.endsWith("]")) {
                String sec = line.substring(1, line.length() - 1).trim();
                inSection = section.equals(sec);
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (!inArray) {
                if (line.startsWith(key)) {
                    int eq = line.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    String rest = line.substring(eq + 1).trim();
                    if (rest.contains("[")) {
                        inArray = true;
                    }
                    buf.append(rest);
                    if (rest.contains("]")) {
                        break;
                    }
                }
            } else {
                buf.append(line);
                if (line.contains("]")) {
                    break;
                }
            }
        }
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(buf.toString());
        while (m.find()) {
            out.add(m.group(1).trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> listTemplateFiles(Path templatesDirPath) {
        List<String> out = new ArrayList<>();
        try (var stream = Files.list(templatesDirPath)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".toml"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.toml$", ""))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException ignored) {
            // empty list fallback
        }
        return out;
    }

    private static List<FieldRule> parseFieldsEnabled(String toml) {
        List<FieldRule> out = new ArrayList<>();
        if (toml == null || toml.isBlank()) {
            return out;
        }
        int idx = toml.indexOf("fields_enabled");
        if (idx < 0) {
            return out;
        }
        String block = toml.substring(idx);
        Matcher m = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*]").matcher(block);
        while (m.find()) {
            out.add(new FieldRule(m.group(1), m.group(2)));
        }
        return out;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record FieldRule(String field, String mode) {
    }

    private record TemplateData(String title, String logo, String background, String primaryColor, List<FieldRule> fields) {
        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"title\":\"").append(escape(title)).append("\",");
            sb.append("\"logo\":\"").append(escape(logo)).append("\",");
            sb.append("\"background\":\"").append(escape(background)).append("\",");
            sb.append("\"primaryColor\":\"").append(escape(primaryColor)).append("\",");
            sb.append("\"fields\":[");
            boolean first = true;
            for (FieldRule f : fields) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"field\":\"").append(escape(f.field())).append("\",\"mode\":\"").append(escape(f.mode())).append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    private record PortalBootstrap(String json, Set<String> templates, String selectedTemplate) {
    }
}
