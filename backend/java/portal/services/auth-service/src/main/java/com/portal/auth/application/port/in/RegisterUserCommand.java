package com.portal.auth.application.port.in;

public record RegisterUserCommand(
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
