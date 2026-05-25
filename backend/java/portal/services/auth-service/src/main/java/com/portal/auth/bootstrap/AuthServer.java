package com.portal.auth.bootstrap;

import com.portal.auth.adapter.in.http.AuthHttpHandler;
import com.portal.auth.adapter.in.http.MqttCommandConsumer;
import com.portal.auth.adapter.out.memory.InMemoryUserRepository;
import com.portal.auth.adapter.out.mqtt.MqttRustUserRepository;
import com.portal.auth.adapter.out.network.SshOpenWrtAccessGateway;
import com.portal.auth.adapter.out.notifications.MosquittoAsyncPublisher;
import com.portal.auth.adapter.out.notifications.Sha256PasswordHasher;
import com.portal.auth.adapter.out.notifications.SmtpSocketEmailSender;
import com.portal.auth.adapter.out.sqlite.AdminSqliteCliRepository;
import com.portal.auth.adapter.out.sqlite.SqliteCliUserRepository;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.application.port.out.OpenWrtAccessGateway;
import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.application.service.AdminSessionService;
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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthServer {
    private static final Path EXTERNAL_CONFIG_DIR = Path.of("/etc/portal-captive");
    private static final Path EXTERNAL_UI_DIR = Path.of("/srv/portal-captive");
    private static final Logger LOGGER = Logger.getLogger(AuthServer.class.getName());

    private AuthServer() {
    }

    public static void run(Path configPath) throws IOException {
        ConfigSelection configSelection = selectConfigPath(configPath);
        Path activeConfigPath = configSelection.path();
        PortalConfig config = PortalConfig.fromToml(activeConfigPath);
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
                RegistrationTemplatePolicy.fromConfig(activeConfigPath)
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
        AdminSqliteCliRepository adminRepository = new AdminSqliteCliRepository(config.sqliteDbPath());
        AdminSessionService adminSessionService = new AdminSessionService(8 * 3600);
        PortalBootstrap bootstrap = buildPortalBootstrap(activeConfigPath, config.registrationTemplate());
        UiSelection uiSelection = selectUiSource();
        Supplier<String> portalIndexHtml = uiSelection.indexHtmlSupplier();
        Supplier<String> portalCoreJs = uiSelection.portalCoreJsSupplier();
        Supplier<String> portalStylesCss = uiSelection.portalStylesCssSupplier();
        Function<String, byte[]> portalAssetBytes = uiSelection.assetBytesSupplier();

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
                bootstrap.templates(),
                adminRepository,
                adminSessionService
        ));
        httpServer.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (repository instanceof MqttRustUserRepository r) {
                r.close();
            }
        }, "auth-shutdown-hook"));

        LOGGER.info("auth-service listening on :" + config.httpPort());
        LOGGER.info("auth-service repository=" + config.userRepositoryType());
        LOGGER.info("auth-service config_source=" + configSelection.source() + " path=" + activeConfigPath);
        if (configSelection.reason() != null) {
            LOGGER.warning("auth-service config_fallback_reason=" + configSelection.reason());
        }
        LOGGER.info("auth-service ui_source=" + uiSelection.source() + " path=" + uiSelection.path());
        if (uiSelection.reason() != null) {
            LOGGER.warning("auth-service ui_fallback_reason=" + uiSelection.reason());
        }
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

    private static Supplier<String> fileSupplier(Path path, String fallback) {
        return () -> {
            try {
                if (!Files.isRegularFile(path)) {
                    return fallback;
                }
                return Files.readString(path, StandardCharsets.UTF_8);
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

    private static Function<String, byte[]> fileBytesSupplier(Path uiRoot) {
        return (path) -> {
            String normalized = path == null ? "" : path.trim();
            if (!normalized.startsWith("/assets/")) {
                return null;
            }
            String rel = normalized.substring("/assets/".length());
            Path file = uiRoot.resolve("assets").resolve(rel).normalize();
            Path assetsRoot = uiRoot.resolve("assets").normalize();
            if (!file.startsWith(assetsRoot)) {
                return null;
            }
            try {
                if (!Files.isRegularFile(file)) {
                    return null;
                }
                return Files.readAllBytes(file);
            } catch (IOException e) {
                return null;
            }
        };
    }

    private static ConfigSelection selectConfigPath(Path bundledConfigPath) {
        Path embedded = bundledConfigPath.toAbsolutePath().normalize();
        Path external = EXTERNAL_CONFIG_DIR.resolve("portal-config.toml");
        String reason = null;
        if (Files.isRegularFile(external)) {
            try {
                validateConfigSource(external);
                return new ConfigSelection(external, "external", null);
            } catch (RuntimeException e) {
                reason = e.getMessage();
            }
        } else {
            reason = "external_config_missing:/etc/portal-captive/portal-config.toml";
        }
        validateConfigSource(embedded);
        return new ConfigSelection(embedded, "embedded", reason);
    }

    private static void validateConfigSource(Path portalConfigPath) {
        PortalConfig config = PortalConfig.fromToml(portalConfigPath);
        RegistrationTemplatePolicy.fromConfig(portalConfigPath);
        buildPortalBootstrap(portalConfigPath, config.registrationTemplate());
    }

    private static UiSelection selectUiSource() {
        Path root = EXTERNAL_UI_DIR.toAbsolutePath().normalize();
        Path index = root.resolve("index.html");
        Path css = root.resolve("styles.css");
        Path js = root.resolve("portal-core.js");
        if (Files.isRegularFile(index) && Files.isRegularFile(css) && Files.isRegularFile(js)) {
            return new UiSelection(
                    "external",
                    root,
                    null,
                    fileSupplier(index, "<!doctype html><html><body><div id='app'></div></body></html>"),
                    fileSupplier(js, "export function mountPortal(root){ root.innerHTML='<p>portal-core.js no disponible</p>'; }"),
                    fileSupplier(css, "body{font-family:sans-serif} #app{max-width:760px;margin:0 auto;padding:1rem}"),
                    fileBytesSupplier(root)
            );
        }
        return new UiSelection(
                "embedded",
                Path.of("classpath:portal"),
                "external_ui_incomplete:/srv/portal-captive/(index.html,styles.css,portal-core.js)",
                resourceSupplier("portal/index.html",
                        "<!doctype html><html><body><div id='app'></div><script>window.__PORTAL_CONFIG__=__PORTAL_BOOTSTRAP_JSON__;</script><script type='module' src='/assets/portal-core.js'></script></body></html>"),
                resourceSupplier("portal/portal-core.js",
                        "export function mountPortal(root){ root.innerHTML='<p>portal-core.js no disponible</p>'; }"),
                resourceSupplier("portal/styles.css",
                        "body{font-family:sans-serif} #app{max-width:760px;margin:0 auto;padding:1rem}"),
                resourceBytesSupplier("portal")
        );
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
        Path templatesCfgPath = resolveFromRoot(repoRoot, templatesCfgFile);
        String templatesCfgRaw = readFile(templatesCfgPath);
        Map<String, String> templatesCfg = SimpleToml.parseFlat(templatesCfgPath);
        String templatesDir = parseKeyInSection(templatesCfgRaw, "templates", "directory");
        if (templatesDir == null || templatesDir.isBlank()) {
            templatesDir = templatesCfg.getOrDefault("templates.directory", "config/templates");
        }
        Path templatesDirPath = resolveFromRoot(repoRoot, templatesDir);

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

        String defaultLang = portalCfg.getOrDefault("registration.default_lang", "es_MX");
        List<String> supportedLangs = parseStringArray(portalCfg.getOrDefault("registration.supported_langs", ""));
        I18nData i18nData = parseI18nData(templatesCfg);
        if (supportedLangs.isEmpty()) {
            supportedLangs = new ArrayList<>(i18nData.fieldsByLang().keySet());
        }
        if (supportedLangs.isEmpty()) {
            supportedLangs = List.of(defaultLang);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"selectedTemplate\":\"").append(escape(selectedTemplate)).append("\",");
        sb.append("\"defaultLang\":\"").append(escape(defaultLang)).append("\",");
        sb.append("\"supportedLangs\":[");
        for (int i = 0; i < supportedLangs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(supportedLangs.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"i18n\":").append(i18nData.toJson()).append(",");
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
        if (portalConfigPath.getFileName() != null &&
                "portal-config.toml".equals(portalConfigPath.getFileName().toString())) {
            Path parent = portalConfigPath.getParent();
            if (parent != null) {
                return parent;
            }
        }
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

    private static Path resolveFromRoot(Path root, String ref) {
        String pathRef = ref == null ? "" : ref.trim();
        if (pathRef.isEmpty()) {
            return root;
        }
        Path candidate1 = root.resolve(pathRef).normalize();
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        if (pathRef.startsWith("config/")) {
            Path candidate2 = root.resolve(pathRef.substring("config/".length())).normalize();
            if (Files.exists(candidate2)) {
                return candidate2;
            }
        }
        Path parent = root.getParent();
        if (parent != null) {
            Path candidate3 = parent.resolve(pathRef).normalize();
            if (Files.exists(candidate3)) {
                return candidate3;
            }
        }
        return candidate1;
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

    private static I18nData parseI18nData(Map<String, String> templatesCfg) {
        Map<String, Map<String, Map<String, String>>> fieldsByLang = new LinkedHashMap<>();
        Map<String, Map<String, String>> messagesByLang = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : templatesCfg.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("i18n.")) {
                continue;
            }
            String[] parts = key.split("\\.");
            if (parts.length < 4) {
                continue;
            }
            String lang = parts[1];
            if ("fields".equals(parts[2]) && parts.length >= 5) {
                String field = parts[3];
                String prop = parts[4];
                fieldsByLang
                        .computeIfAbsent(lang, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(field, ignored -> new LinkedHashMap<>())
                        .put(prop, e.getValue());
                continue;
            }
            if ("messages".equals(parts[2]) && parts.length >= 4) {
                String msgKey = parts[3];
                messagesByLang
                        .computeIfAbsent(lang, ignored -> new LinkedHashMap<>())
                        .put(msgKey, e.getValue());
            }
        }
        return new I18nData(fieldsByLang, messagesByLang);
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

    private record I18nData(
            Map<String, Map<String, Map<String, String>>> fieldsByLang,
            Map<String, Map<String, String>> messagesByLang
    ) {
        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");

            sb.append("\"fields\":{");
            boolean firstLang = true;
            for (Map.Entry<String, Map<String, Map<String, String>>> langEntry : fieldsByLang.entrySet()) {
                if (!firstLang) sb.append(",");
                firstLang = false;
                sb.append("\"").append(escape(langEntry.getKey())).append("\":{");
                boolean firstField = true;
                for (Map.Entry<String, Map<String, String>> fieldEntry : langEntry.getValue().entrySet()) {
                    if (!firstField) sb.append(",");
                    firstField = false;
                    sb.append("\"").append(escape(fieldEntry.getKey())).append("\":{");
                    boolean firstProp = true;
                    for (Map.Entry<String, String> propEntry : fieldEntry.getValue().entrySet()) {
                        if (!firstProp) sb.append(",");
                        firstProp = false;
                        sb.append("\"").append(escape(propEntry.getKey())).append("\":\"")
                                .append(escape(propEntry.getValue())).append("\"");
                    }
                    sb.append("}");
                }
                sb.append("}");
            }
            sb.append("},");

            sb.append("\"messages\":{");
            firstLang = true;
            for (Map.Entry<String, Map<String, String>> langEntry : messagesByLang.entrySet()) {
                if (!firstLang) sb.append(",");
                firstLang = false;
                sb.append("\"").append(escape(langEntry.getKey())).append("\":{");
                boolean firstMsg = true;
                for (Map.Entry<String, String> msgEntry : langEntry.getValue().entrySet()) {
                    if (!firstMsg) sb.append(",");
                    firstMsg = false;
                    sb.append("\"").append(escape(msgEntry.getKey())).append("\":\"")
                            .append(escape(msgEntry.getValue())).append("\"");
                }
                sb.append("}");
            }
            sb.append("}");

            sb.append("}");
            return sb.toString();
        }
    }

    private record ConfigSelection(Path path, String source, String reason) {
    }

    private record UiSelection(
            String source,
            Path path,
            String reason,
            Supplier<String> indexHtmlSupplier,
            Supplier<String> portalCoreJsSupplier,
            Supplier<String> portalStylesCssSupplier,
            Function<String, byte[]> assetBytesSupplier
    ) {
    }
}
