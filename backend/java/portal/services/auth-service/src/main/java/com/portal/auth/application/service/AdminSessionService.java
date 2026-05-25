package com.portal.auth.application.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminSessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final int ttlSeconds;

    public AdminSessionService(int ttlSeconds) {
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public String issue(String username, String role) {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.put(token, new Session(username, role, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    public Optional<Session> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session s = sessions.get(token);
        if (s == null) return Optional.empty();
        if (Instant.now().isAfter(s.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public record Session(String username, String role, Instant expiresAt) {
        public boolean isAdmin() {
            return "admin".equalsIgnoreCase(role);
        }
    }
}

