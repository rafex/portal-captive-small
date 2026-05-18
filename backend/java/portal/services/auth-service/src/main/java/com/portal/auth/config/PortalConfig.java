package com.portal.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record PortalConfig(
        int httpPort,
        int sessionTtlSeconds,
        String smtpHost,
        int smtpPort,
        String smtpFrom,
        String openWrtHost,
        int openWrtPort,
        String openWrtUser,
        String mqttHost,
        int mqttPort,
        String mqttTopicRegister,
        String mqttTopicLogin,
        String mqttTopicIssuePassword,
        String userRepositoryType,
        String sqliteDbPath
) {
    public static PortalConfig fromToml(Path path) {
        int httpPort = 8080;
        int sessionTtlSeconds = 3600;
        String smtpHost = "127.0.0.1";
        int smtpPort = 25;
        String smtpFrom = "no-reply@example.com";
        String openWrtHost = "192.168.1.1";
        int openWrtPort = 22;
        String openWrtUser = "root";
        String mqttHost = "127.0.0.1";
        int mqttPort = 1883;
        String mqttTopicRegister = "portal/register/in";
        String mqttTopicLogin = "portal/login/in";
        String mqttTopicIssuePassword = "portal/password/issue/in";
        String userRepositoryType = "sqlite";
        String sqliteDbPath = "data/auth-service.db";
        String section = "";

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                if (l.startsWith("[") && l.endsWith("]")) {
                    section = l.substring(1, l.length() - 1).trim();
                    continue;
                }

                if ("server".equals(section) && l.startsWith("http_port")) {
                    httpPort = parseInt(l);
                } else if ("session".equals(section) && l.startsWith("internet_ttl_seconds")) {
                    sessionTtlSeconds = parseInt(l);
                } else if ("smtp".equals(section) && l.startsWith("host")) {
                    smtpHost = parseString(l);
                } else if ("smtp".equals(section) && l.startsWith("port")) {
                    smtpPort = parseInt(l);
                } else if ("smtp".equals(section) && l.startsWith("from")) {
                    smtpFrom = parseString(l);
                } else if ("openwrt".equals(section) && l.startsWith("ssh_host")) {
                    openWrtHost = parseString(l);
                } else if ("openwrt".equals(section) && l.startsWith("ssh_port")) {
                    openWrtPort = parseInt(l);
                } else if ("openwrt".equals(section) && l.startsWith("ssh_user")) {
                    openWrtUser = parseString(l);
                } else if ("mqtt".equals(section) && l.startsWith("host")) {
                    mqttHost = parseString(l);
                } else if ("mqtt".equals(section) && l.startsWith("port")) {
                    mqttPort = parseInt(l);
                } else if ("mqtt".equals(section) && l.startsWith("topic_register")) {
                    mqttTopicRegister = parseString(l);
                } else if ("mqtt".equals(section) && l.startsWith("topic_login")) {
                    mqttTopicLogin = parseString(l);
                } else if ("mqtt".equals(section) && l.startsWith("topic_issue_password")) {
                    mqttTopicIssuePassword = parseString(l);
                } else if ("repository".equals(section) && l.startsWith("type")) {
                    userRepositoryType = parseString(l);
                } else if ("repository".equals(section) && l.startsWith("sqlite_db_path")) {
                    sqliteDbPath = parseString(l);
                }
            }
        } catch (IOException ignored) {
        }

        return new PortalConfig(
                httpPort, sessionTtlSeconds, smtpHost, smtpPort, smtpFrom,
                openWrtHost, openWrtPort, openWrtUser,
                mqttHost, mqttPort, mqttTopicRegister, mqttTopicLogin, mqttTopicIssuePassword,
                userRepositoryType, sqliteDbPath
        );
    }

    private static int parseInt(String line) {
        return Integer.parseInt(line.substring(line.indexOf('=') + 1).trim().replace("\"", ""));
    }

    private static String parseString(String line) {
        String raw = line.substring(line.indexOf('=') + 1).trim();
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}
