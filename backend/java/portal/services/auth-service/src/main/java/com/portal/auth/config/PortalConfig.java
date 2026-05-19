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
        String userRepositoryType,
        String sqliteDbPath,
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
                strOrDefault(kv, "mqtt.host", "127.0.0.1"),
                intOrDefault(kv, "mqtt.port", 1883),
                strOrDefault(kv, "mqtt.topic_register", "portal/register/in"),
                strOrDefault(kv, "mqtt.topic_login", "portal/login/in"),
                strOrDefault(kv, "mqtt.topic_issue_password", "portal/password/issue/in"),
                strOrDefault(kv, "mqtt.topic_register_out", "portal/register/out"),
                strOrDefault(kv, "mqtt.topic_login_out", "portal/login/out"),
                strOrDefault(kv, "mqtt.topic_issue_password_out", "portal/password/issue/out"),
                strOrDefault(kv, "repository.type", "sqlite"),
                strOrDefault(kv, "repository.sqlite_db_path", "data/auth-service.db"),
                strOrDefault(kv, "db_mqtt.user_request_topic", "portal/db/user/request"),
                intOrDefault(kv, "db_mqtt.response_wait_seconds", 5),
                intOrDefault(kv, "db_mqtt.max_retries", 2),
                intOrDefault(kv, "db_mqtt.retry_backoff_ms", 100)
        );
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
