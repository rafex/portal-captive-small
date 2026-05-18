package com.portal.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record User(
        String userId,
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
        String passwordHash,
        String passwordSalt,
        Instant createdAt,
        Instant updatedAt
) {
    public User {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(passwordSalt, "passwordSalt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User newUser(String firstName, String lastName, Integer age, String email, String phone,
                               String mobile, String address, String socialFacebook, String socialInstagram,
                               String socialTiktok, String socialX, String passwordHash, String passwordSalt) {
        Instant now = Instant.now();
        return new User(
                UUID.randomUUID().toString(),
                firstName,
                lastName,
                age,
                email,
                phone,
                mobile,
                address,
                socialFacebook,
                socialInstagram,
                socialTiktok,
                socialX,
                passwordHash,
                passwordSalt,
                now,
                now
        );
    }
}
