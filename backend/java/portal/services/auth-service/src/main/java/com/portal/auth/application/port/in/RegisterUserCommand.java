package com.portal.auth.application.port.in;

public record RegisterUserCommand(
        String template,
        String deviceIp,
        String deviceUuid,
        String deviceFingerprint,
        String userAgent,
        String firstName,
        String lastName,
        Integer age,
        String email,
        String phone,
        String mobile,
        String address,
        String socialFacebook,
        String socialInstagram,
        String socialTiktok,
        String socialX,
        String rawPassword
) {
}
