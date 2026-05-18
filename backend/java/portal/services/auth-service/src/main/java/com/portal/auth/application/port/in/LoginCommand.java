package com.portal.auth.application.port.in;

public record LoginCommand(String identifier, String rawPassword) {
}
