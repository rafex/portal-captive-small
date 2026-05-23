package com.portal.auth.application.service;

import com.portal.auth.application.port.in.RegisterUserCommand;

import java.util.Locale;
import java.util.Set;

final class RegistrationTemplatePolicy {
    private static final Set<String> ALLOWED = Set.of(
            "hotel", "restaurant", "restaurante", "school", "escuela", "casa", "personalizado",
            "coworking", "cafe", "airport", "mall", "library", "hospital", "gym", "event",
            "community_mesh", "university", "gaming"
    );

    void validate(RegisterUserCommand command) {
        String template = normalize(command.template());
        if (template == null || !ALLOWED.contains(template)) {
            throw new IllegalArgumentException("invalid_template");
        }

        if (isBlank(command.firstName()) && isBlank(command.email()) && isBlank(command.phone()) && isBlank(command.mobile())) {
            throw new IllegalArgumentException("identity_required");
        }

        switch (template) {
            case "hotel" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.email(), "email_required_hotel");
                require(command.address(), "address_required_hotel");
                require(command.mobile(), "mobile_required_hotel");
            }
            case "restaurante", "restaurant" -> {
                require(command.email(), "email_required_restaurante");
                require(command.address(), "address_required_restaurante");
                require(command.phone(), "phone_required_restaurante");
            }
            case "escuela", "school" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.email(), "email_required_escuela");
                if (command.age() == null || command.age() < 5) {
                    throw new IllegalArgumentException("age_required_escuela");
                }
            }
            case "casa" -> {
                require(command.firstName(), "first_name_required");
                require(command.lastName(), "last_name_required");
                require(command.rawPassword(), "password_required");
                require(command.mobile(), "mobile_required_casa");
            }
            case "personalizado" -> {
                // intentionally minimal; shared baseline validation already applied
            }
            case "coworking", "cafe", "airport", "mall", "library", "hospital", "gym", "event",
                 "community_mesh", "university", "gaming" -> {
                // baseline-only validation for dynamic templates.
            }
            default -> throw new IllegalArgumentException("invalid_template");
        }

        validateSocial(command.socialFacebook(), "facebook_invalid");
        validateSocial(command.socialInstagram(), "instagram_invalid");
        validateSocial(command.socialTiktok(), "tiktok_invalid");
        validateSocial(command.socialX(), "x_invalid");
    }

    private static void validateSocial(String value, String error) {
        if (isBlank(value)) {
            return;
        }
        String v = value.trim();
        if (v.contains(" ") || v.length() < 2 || v.length() > 64) {
            throw new IllegalArgumentException(error);
        }
    }

    private static void require(String value, String error) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(error);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
