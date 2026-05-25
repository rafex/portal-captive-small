package com.portal.auth.config;

import com.portal.auth.shared.SimpleToml;

import java.nio.file.Path;
import java.util.Map;

public record PortalConfig(
        int httpPort,
        int sessionTtlSeconds,
        String smtpHost,
        int smtpPort,
        String smtpFrom,
        String openWrtHost,
        int openWrtPort,
        String openWrtUser,
        boolean openWrtEnabled,
        String mqttHost,
        int mqttPort,
        String mqttTopicRegister,
        String mqttTopicLogin,
        String mqttTopicIssuePassword,
        String mqttTopicRegisterOut,
        String mqttTopicLoginOut,
        String mqttTopicIssuePasswordOut,
        String registrationTemplate,
        String userRepositoryType,
        String sqliteDbPath,
        int usersTtlSeconds,
        String dbMqttUserRequestTopic,
        int dbMqttResponseWaitSeconds,
        int dbMqttMaxRetries,
        int dbMqttRetryBackoffMs
) {
    public static PortalConfig fromToml(Path path) {
        Map<String, String> kv = SimpleToml.parseFlat(path);

        return new PortalConfig(
                intOrDefault(kv, "server.http_port", 8080),
                intOrDefault(kv, "session.internet_ttl_seconds", 3600),
                strOrDefault(kv, "smtp.host", "127.0.0.1"),
                intOrDefault(kv, "smtp.port", 25),
                strOrDefault(kv, "smtp.from", "no-reply@example.com"),
                strOrDefault(kv, "openwrt.ssh_host", "192.168.1.1"),
                intOrDefault(kv, "openwrt.ssh_port", 22),
                strOrDefault(kv, "openwrt.ssh_user", "root"),
                boolOrDefault(kv, "openwrt.enabled", true),
                envOrKey("MQTT_HOST", kv, "mqtt.host", "127.0.0.1"),
                envIntOrKey("MQTT_PORT", kv, "mqtt.port", 1883),
                envOrKey("MQTT_TOPIC_REGISTER", kv, "mqtt.topic_register", "portal/register/in"),
                envOrKey("MQTT_TOPIC_LOGIN", kv, "mqtt.topic_login", "portal/login/in"),
                envOrKey("MQTT_TOPIC_ISSUE_PASSWORD", kv, "mqtt.topic_issue_password", "portal/password/issue/in"),
                envOrKey("MQTT_TOPIC_REGISTER_OUT", kv, "mqtt.topic_register_out", "portal/register/out"),
                envOrKey("MQTT_TOPIC_LOGIN_OUT", kv, "mqtt.topic_login_out", "portal/login/out"),
                envOrKey("MQTT_TOPIC_ISSUE_PASSWORD_OUT", kv, "mqtt.topic_issue_password_out", "portal/password/issue/out"),
                strOrDefault(kv, "registration.template", "hotel"),
                strOrDefault(kv, "repository.type", "sqlite"),
                strOrDefault(kv, "repository.sqlite_db_path", "data/auth-service.db"),
                intOrDefault(kv, "repository.users_ttl_seconds", 3600),
                envOrKey("DB_USER_REQUEST_TOPIC", kv, "db_mqtt.user_request_topic", "portal/db/user/request"),
                envIntOrKey("DB_MQTT_RESPONSE_WAIT_SECONDS", kv, "db_mqtt.response_wait_seconds", 5),
                envIntOrKey("DB_MQTT_MAX_RETRIES", kv, "db_mqtt.max_retries", 2),
                envIntOrKey("DB_MQTT_RETRY_BACKOFF_MS", kv, "db_mqtt.retry_backoff_ms", 100)
        );
    }

    private static String envOrKey(String env, Map<String, String> kv, String key, String def) {
        String envValue = System.getenv(env);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return strOrDefault(kv, key, def);
    }

    private static int envIntOrKey(String env, Map<String, String> kv, String key, int def) {
        String envValue = System.getenv(env);
        if (envValue != null && !envValue.isBlank()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException ignored) {
                // fallback to toml/default
            }
        }
        return intOrDefault(kv, key, def);
    }

    private static String strOrDefault(Map<String, String> kv, String key, String def) {
        String v = kv.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return v;
    }

    private static int intOrDefault(Map<String, String> kv, String key, int def) {
        String v = kv.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolOrDefault(Map<String, String> kv, String key, boolean def) {
        String v = kv.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return Boolean.parseBoolean(v);
    }
}
