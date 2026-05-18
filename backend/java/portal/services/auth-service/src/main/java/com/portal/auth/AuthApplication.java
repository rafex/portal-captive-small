package com.portal.auth;

import com.portal.auth.bootstrap.AuthServer;

import java.nio.file.Path;

public final class AuthApplication {
    private AuthApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("config/portal-config.toml");
        AuthServer.run(configPath);
    }
}
