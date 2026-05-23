package com.portal.auth.application.service;

import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.shared.SimpleToml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegistrationTemplatePolicy {
    private static final Pattern TEMPLATE_SLUG = Pattern.compile("^[a-z0-9_\\-]{2,64}$");
    private static final Pattern FIELD_PAIR = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*]");
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Map<String, Set<String>> requiredByTemplate;
    private final Map<String, FieldSpec> fieldSpecs;

    public RegistrationTemplatePolicy() {
        this.requiredByTemplate = new HashMap<>();
        this.fieldSpecs = new HashMap<>();
    }

    public RegistrationTemplatePolicy(Map<String, Set<String>> requiredByTemplate) {
        this.requiredByTemplate = requiredByTemplate;
        this.fieldSpecs = new HashMap<>();
    }

    public RegistrationTemplatePolicy(Map<String, Set<String>> requiredByTemplate, Map<String, FieldSpec> fieldSpecs) {
        this.requiredByTemplate = requiredByTemplate;
        this.fieldSpecs = fieldSpecs;
    }

    public static RegistrationTemplatePolicy fromConfig(Path portalConfigPath) {
        Path repoRoot = resolveProjectRoot(portalConfigPath.toAbsolutePath());
        String portalRaw = readFile(portalConfigPath);
        Map<String, String> portalKv = SimpleToml.parseFlat(portalConfigPath);

        String templatesCfgFile = parseKeyInSection(portalRaw, "templates_config", "file");
        if (isBlank(templatesCfgFile)) {
            templatesCfgFile = portalKv.getOrDefault("templates_config.file", "config/templates-config.toml");
        }
        Path templatesCfgPath = repoRoot.resolve(templatesCfgFile).normalize();
        String templatesCfgRaw = readFile(templatesCfgPath);
        Map<String, String> templatesCfgKv = SimpleToml.parseFlat(templatesCfgPath);

        String templatesDir = parseKeyInSection(templatesCfgRaw, "templates", "directory");
        if (isBlank(templatesDir)) {
            templatesDir = templatesCfgKv.getOrDefault("templates.directory", "config/templates");
        }
        Path templatesDirPath = repoRoot.resolve(templatesDir).normalize();

        List<String> templates = parseArrayInSection(templatesCfgRaw, "templates", "available");
        if (templates.isEmpty()) {
            templates = listTemplateFiles(templatesDirPath);
        }
        if (templates.isEmpty()) {
            throw new IllegalStateException("missing_templates_configuration");
        }

        Map<String, Set<String>> required = new HashMap<>();
        Map<String, FieldSpec> specs = parseFieldSpecs(templatesCfgRaw);
        if (specs.isEmpty()) {
            throw new IllegalStateException("missing_fields_configuration");
        }
        for (String template : templates) {
            String raw = readFile(templatesDirPath.resolve(template + ".toml"));
            if (isBlank(raw)) {
                throw new IllegalStateException("missing_template_file_" + template);
            }
            required.put(template, parseRequiredFields(raw));
        }
        return new RegistrationTemplatePolicy(required, specs);
    }

    public void validate(RegisterUserCommand command) {
        String template = normalize(command.template());
        if (template == null || !TEMPLATE_SLUG.matcher(template).matches()) {
            throw new IllegalArgumentException("invalid_template");
        }

        if (requiredByTemplate.isEmpty()) {
            throw new IllegalStateException("missing_templates_configuration");
        }

        Set<String> required = requiredByTemplate.get(template);
        if (required == null) {
            throw new IllegalArgumentException("invalid_template");
        }

        for (String field : required) {
            switch (field) {
                case "name", "firstname", "first_name", "nickname" -> require(command.firstName(), "first_name_required");
                case "lastname", "last_name" -> require(command.lastName(), "last_name_required");
                case "email" -> require(command.email(), "email_required");
                case "phone" -> require(command.phone(), "phone_required");
                case "mobile" -> require(command.mobile(), "mobile_required");
                case "password" -> require(command.rawPassword(), "password_required");
                case "room", "address" -> require(command.address(), "address_required");
                case "age" -> {
                    if (command.age() == null || command.age() <= 0) {
                        throw new IllegalArgumentException("age_required");
                    }
                }
                case "social" -> {
                    if (isBlank(command.socialFacebook()) &&
                            isBlank(command.socialInstagram()) &&
                            isBlank(command.socialTiktok()) &&
                            isBlank(command.socialX())) {
                        throw new IllegalArgumentException("social_required");
                    }
                }
                case "terms" -> {
                    // El backend no recibe explícitamente terms; el frontend lo valida.
                }
                default -> {
                    // Campo desconocido: no bloquear para mantener forward-compatibility.
                }
            }
        }
        validateFieldSpecs(command);

        // Sanitiza formatos sociales cuando vienen presentes.
        validateSocial(command.socialFacebook(), "facebook_invalid");
        validateSocial(command.socialInstagram(), "instagram_invalid");
        validateSocial(command.socialTiktok(), "tiktok_invalid");
        validateSocial(command.socialX(), "x_invalid");

        // Para login posterior, si se envía password debe haber al menos email o phone/mobile.
        if (!isBlank(command.rawPassword()) &&
                isBlank(command.email()) && isBlank(command.phone()) && isBlank(command.mobile())) {
            throw new IllegalArgumentException("identifier_required_with_password");
        }
    }

    private void validateFieldSpecs(RegisterUserCommand command) {
        if (fieldSpecs.isEmpty()) {
            return;
        }
        for (Map.Entry<String, FieldSpec> e : fieldSpecs.entrySet()) {
            String field = e.getKey();
            FieldSpec spec = e.getValue();
            String value = fieldValue(command, field);
            validateTypedField(field, spec, value);
        }
    }

    private static String fieldValue(RegisterUserCommand command, String field) {
        return switch (field) {
            case "name", "firstname", "first_name", "nickname" -> command.firstName();
            case "lastname", "last_name" -> command.lastName();
            case "email" -> command.email();
            case "phone" -> command.phone();
            case "mobile" -> command.mobile();
            case "password" -> command.rawPassword();
            case "room", "address" -> command.address();
            case "social" -> firstNonBlank(
                    command.socialFacebook(),
                    command.socialInstagram(),
                    command.socialTiktok(),
                    command.socialX()
            );
            default -> null;
        };
    }

    private static void validateTypedField(String field, FieldSpec spec, String value) {
        if (isBlank(value)) {
            return;
        }
        String v = value.trim();
        if (spec.minLength != null && v.length() < spec.minLength) {
            throw new IllegalArgumentException(field + "_too_short");
        }
        if (spec.maxLength != null && v.length() > spec.maxLength) {
            throw new IllegalArgumentException(field + "_too_long");
        }
        if ("email".equals(spec.type) && !SIMPLE_EMAIL.matcher(v).matches()) {
            throw new IllegalArgumentException("email_invalid");
        }
    }

    private static Set<String> parseRequiredFields(String templateToml) {
        Set<String> out = new HashSet<>();
        if (isBlank(templateToml)) {
            return out;
        }
        Matcher m = FIELD_PAIR.matcher(templateToml);
        while (m.find()) {
            String field = normalize(m.group(1));
            String mode = normalize(m.group(2));
            if ("required".equals(mode) && field != null) {
                out.add(field);
            }
        }
        return out;
    }

    private static Map<String, FieldSpec> parseFieldSpecs(String templatesCfgRaw) {
        Map<String, FieldSpec> out = new HashMap<>();
        if (isBlank(templatesCfgRaw)) {
            return out;
        }
        String currentField = null;
        for (String rawLine : templatesCfgRaw.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                String sec = line.substring(1, line.length() - 1).trim();
                if (sec.startsWith("fields.")) {
                    currentField = normalize(sec.substring("fields.".length()));
                    out.putIfAbsent(currentField, new FieldSpec(null, null, null));
                } else {
                    currentField = null;
                }
                continue;
            }
            if (currentField == null) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            FieldSpec current = out.getOrDefault(currentField, new FieldSpec(null, null, null));
            switch (key) {
                case "type" -> out.put(currentField, new FieldSpec(unquote(value), current.minLength, current.maxLength));
                case "min_length" -> out.put(currentField, new FieldSpec(current.type, parseInteger(value), current.maxLength));
                case "max_length" -> out.put(currentField, new FieldSpec(current.type, current.minLength, parseInteger(value)));
                default -> {
                }
            }
        }
        return out;
    }

    private static String parseKeyInSection(String toml, String section, String key) {
        if (isBlank(toml)) {
            return null;
        }
        boolean inSection = false;
        for (String rawLine : toml.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                inSection = section.equals(line.substring(1, line.length() - 1).trim());
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith(key + " ")) {
                int eq = line.indexOf('=');
                if (eq < 0) return null;
                String v = line.substring(eq + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    return v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }

    private static List<String> parseArrayInSection(String toml, String section, String key) {
        List<String> out = new ArrayList<>();
        if (isBlank(toml)) {
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
                inSection = section.equals(line.substring(1, line.length() - 1).trim());
                continue;
            }
            if (!inSection) continue;

            if (!inArray) {
                if (line.startsWith(key)) {
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String rest = line.substring(eq + 1).trim();
                    if (rest.contains("[")) inArray = true;
                    buf.append(rest);
                    if (rest.contains("]")) break;
                }
            } else {
                buf.append(line);
                if (line.contains("]")) break;
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
        } catch (Exception ignored) {
        }
        return out;
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
        if (p == null) return Path.of(".").toAbsolutePath();
        if ("config".equals(p.getFileName() != null ? p.getFileName().toString() : "")) {
            Path maybeBackend = p.getParent();
            if (maybeBackend != null && "backend".equals(maybeBackend.getFileName() != null ? maybeBackend.getFileName().toString() : "")) {
                Path root = maybeBackend.getParent();
                if (root != null) return root;
            }
        }
        Path parent = p.getParent();
        return parent != null ? parent : Path.of(".").toAbsolutePath();
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static void validateSocial(String value, String error) {
        if (isBlank(value)) return;
        String v = value.trim();
        if (v.contains(" ") || v.length() < 2 || v.length() > 64) {
            throw new IllegalArgumentException(error);
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String unquote(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1).trim().toLowerCase(Locale.ROOT);
        }
        return v.toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static void require(String value, String error) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(error);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value) {
        if (isBlank(value)) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class FieldSpec {
        private final String type;
        private final Integer minLength;
        private final Integer maxLength;

        public FieldSpec(String type, Integer minLength, Integer maxLength) {
            this.type = type;
            this.minLength = minLength;
            this.maxLength = maxLength;
        }
    }
}
