package com.portal.auth.application.port.out;

public interface PasswordHasher {
    String salt();

    String hash(String rawPassword, String salt);
}
