package com.portal.auth.application.port.out;

public interface OpenWrtAccessGateway {
    void allowSession(String userId, int ttlSeconds);
}
