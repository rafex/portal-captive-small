package com.portal.auth.application.service;

import com.portal.auth.application.port.in.IssuePasswordUseCase;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.LoginUseCase;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.in.RegisterUserUseCase;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.application.port.out.EmailSender;
import com.portal.auth.application.port.out.OpenWrtAccessGateway;
import com.portal.auth.application.port.out.PasswordHasher;
import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

public final class AuthService implements RegisterUserUseCase, LoginUseCase, IssuePasswordUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AsyncEventPublisher asyncEventPublisher;
    private final EmailSender emailSender;
    private final OpenWrtAccessGateway openWrtAccessGateway;
    private final int sessionTtlSeconds;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       AsyncEventPublisher asyncEventPublisher,
                       EmailSender emailSender,
                       OpenWrtAccessGateway openWrtAccessGateway,
                       int sessionTtlSeconds) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.asyncEventPublisher = asyncEventPublisher;
        this.emailSender = emailSender;
        this.openWrtAccessGateway = openWrtAccessGateway;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Override
    public String register(RegisterUserCommand command) {
        String normalizedEmail = normalize(command.email());
        String normalizedPhone = normalize(command.phone());

        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("email_already_exists");
        }
        if (normalizedPhone != null && userRepository.findByPhone(normalizedPhone).isPresent()) {
            throw new IllegalArgumentException("phone_already_exists");
        }

        String salt = passwordHasher.salt();
        String hash = passwordHasher.hash(command.rawPassword(), salt);
        User user = User.newUser(
                command.firstName(),
                command.lastName(),
                command.age(),
                normalizedEmail,
                normalizedPhone,
                normalize(command.mobile()),
                normalize(command.address()),
                normalize(command.socialFacebook()),
                normalize(command.socialInstagram()),
                normalize(command.socialTiktok()),
                normalize(command.socialX()),
                hash,
                salt
        );
        userRepository.save(user);
        asyncEventPublisher.publish("portal/register", "{\"userId\":\"" + user.userId() + "\"}");
        return user.userId();
    }

    @Override
    public LoginResult login(LoginCommand command) {
        String identifier = normalize(command.identifier());
        if (identifier == null) {
            return new LoginResult(false, null, "missing_identifier");
        }

        User user = identifier.contains("@")
                ? userRepository.findByEmail(identifier).orElse(null)
                : userRepository.findByPhone(identifier).orElse(null);

        if (user == null) {
            return new LoginResult(false, null, "user_not_found");
        }

        String expectedHash = passwordHasher.hash(command.rawPassword(), user.passwordSalt());
        if (!expectedHash.equals(user.passwordHash())) {
            return new LoginResult(false, null, "invalid_password");
        }

        openWrtAccessGateway.allowSession(user.userId(), sessionTtlSeconds);
        asyncEventPublisher.publish("portal/login", "{\"userId\":\"" + user.userId() + "\"}");
        return new LoginResult(true, user.userId(), "ok");
    }

    @Override
    public String issueTemporaryPassword(String email) {
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        byte[] token = new byte[9];
        new SecureRandom().nextBytes(token);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(token);

        String salt = passwordHasher.salt();
        String hash = passwordHasher.hash(temporaryPassword, salt);
        User updated = new User(
                user.userId(), user.firstName(), user.lastName(), user.age(), user.email(), user.phone(),
                user.mobile(), user.address(), user.socialFacebook(), user.socialInstagram(), user.socialTiktok(),
                user.socialX(), hash, salt, user.createdAt(), java.time.Instant.now());

        userRepository.save(updated);
        emailSender.sendTemporaryPassword(normalizedEmail, temporaryPassword);
        return temporaryPassword;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
