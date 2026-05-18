package com.portal.auth.adapter.out.network;

import com.portal.auth.application.port.out.OpenWrtAccessGateway;

import java.io.IOException;

public final class SshOpenWrtAccessGateway implements OpenWrtAccessGateway {
    private final String host;
    private final int port;
    private final String user;

    public SshOpenWrtAccessGateway(String host, int port, String user) {
        this.host = host;
        this.port = port;
        this.user = user;
    }

    @Override
    public void allowSession(String userId, int ttlSeconds) {
        String command = "uci add firewall rule && " +
                "uci set firewall.@rule[-1].name='portal-" + userId + "' && " +
                "uci set firewall.@rule[-1].src='lan' && " +
                "uci set firewall.@rule[-1].target='ACCEPT' && " +
                "uci commit firewall && /etc/init.d/firewall reload";
        ProcessBuilder builder = new ProcessBuilder(
                "ssh",
                "-p",
                String.valueOf(port),
                user + "@" + host,
                command
        );
        try {
            Process process = builder.start();
            int code = process.waitFor();
            if (code != 0) {
                throw new IllegalStateException("openwrt_uci_failed code=" + code + " ttl=" + ttlSeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("openwrt_ssh_interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("openwrt_ssh_failed", e);
        }
    }
}
