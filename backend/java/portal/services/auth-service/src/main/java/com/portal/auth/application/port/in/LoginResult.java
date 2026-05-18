package com.portal.auth.application.port.in;

public record LoginResult(boolean authenticated, String userId, String reason) {
}
