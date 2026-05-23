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

    private final Map<String, Set<String>> requiredByTemplate;

    public RegistrationTemplatePolicy() {
        this.requiredByTemplate = new HashMap<>();
    }

    public RegistrationTemplatePolicy(Map<String, Set<String>> requiredByTemplate) {
        this.requiredByTemplate = requiredByTemplate;
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

        Map<String, Set<String>> required = new HashMap<>();
        for (String template : templates) {
            String raw = readFile(templatesDirPath.resolve(template + ".toml"));
            required.put(template, parseRequiredFields(raw));
        }
        return new RegistrationTemplatePolicy(required);
    }

    public void validate(RegisterUserCommand command) {
        String template = normalize(command.template());
        if (template == null || !TEMPLATE_SLUG.matcher(template).matches()) {
            throw new IllegalArgumentException("invalid_template");
        }

        if (requiredByTemplate.isEmpty()) {
            legacyValidate(command, template);
            return;
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

    private static void legacyValidate(RegisterUserCommand command, String template) {
        switch (template) {
            case "hotel" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.email(), "email_required_hotel");
                require(command.address(), "address_required_hotel");
                require(command.mobile(), "mobile_required_hotel");
            }
            case "restaurante", "restaurant" -> {
                require(command.email(), "email_required_restaurante");
                require(command.address(), "address_required_restaurante");
                require(command.phone(), "phone_required_restaurante");
            }
            case "escuela", "school" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.email(), "email_required_escuela");
                if (command.age() == null || command.age() < 5) {
                    throw new IllegalArgumentException("age_required_escuela");
                }
            }
            case "casa" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.mobile(), "mobile_required_casa");
            }
            default -> {
                // Minimal fallback for unknown templates in tests/local contexts.
                if (!isBlank(command.rawPassword()) &&
                        isBlank(command.email()) && isBlank(command.phone()) && isBlank(command.mobile())) {
                    throw new IllegalArgumentException("identifier_required_with_password");
                }
            }
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
}
