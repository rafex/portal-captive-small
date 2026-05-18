package com.portal.auth.adapter.out.network;

import com.portal.auth.application.port.out.OpenWrtAccessGateway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class SshOpenWrtAccessGateway implements OpenWrtAccessGateway {
    private final String host;
    private final int port;
    private final String user;
    private final boolean enabled;

    public SshOpenWrtAccessGateway(String host, int port, String user, boolean enabled) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.enabled = enabled;
    }

    @Override
    public void allowSession(String userId, int ttlSeconds) {
        if (!enabled) {
            return;
        }

        String safeUserId = sanitize(userId);
        int safeTtl = Math.max(60, ttlSeconds);

        String remoteScript = "set -e; " +
                "RULE=$(uci add firewall rule); " +
                "cleanup(){ uci -q delete firewall.${RULE}; uci commit firewall; /etc/init.d/firewall reload; }; " +
                "trap cleanup ERR; " +
                "uci set firewall.${RULE}.name='portal-" + safeUserId + "'; " +
                "uci set firewall.${RULE}.src='lan'; " +
                "uci set firewall.${RULE}.target='ACCEPT'; " +
                "uci commit firewall; /etc/init.d/firewall reload; " +
                "trap - ERR; " +
                "(sleep " + safeTtl + "; uci -q delete firewall.${RULE}; uci commit firewall; /etc/init.d/firewall reload) >/dev/null 2>&1 &";

        ProcessBuilder builder = new ProcessBuilder(
                "ssh",
                "-o", "BatchMode=yes",
                "-o", "ConnectTimeout=5",
                "-o", "ServerAliveInterval=5",
                "-o", "ServerAliveCountMax=1",
                "-p", String.valueOf(port),
                user + "@" + host,
                "sh", "-c", remoteScript
        );

        try {
            Process process = builder.start();
            String stderr = readAll(process.getErrorStream());
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("openwrt_uci_failed code=" + code + " err=" + stderr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("openwrt_ssh_interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("openwrt_ssh_failed", e);
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        }
    }
}
