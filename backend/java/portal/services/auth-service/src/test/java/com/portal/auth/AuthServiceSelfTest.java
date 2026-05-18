package com.portal.auth;

import com.portal.auth.adapter.out.memory.InMemoryUserRepository;
import com.portal.auth.adapter.out.notifications.Sha256PasswordHasher;
import com.portal.auth.application.port.in.LoginCommand;
import com.portal.auth.application.port.in.LoginResult;
import com.portal.auth.application.port.in.RegisterUserCommand;
import com.portal.auth.application.port.out.AsyncEventPublisher;
import com.portal.auth.application.port.out.EmailSender;
import com.portal.auth.application.port.out.OpenWrtAccessGateway;
import com.portal.auth.application.service.AuthService;

import java.util.ArrayList;
import java.util.List;

public final class AuthServiceSelfTest {
    private AuthServiceSelfTest() {
    }

    public static void main(String[] args) {
        shouldRegisterAndLoginCasaTemplate();
        shouldRejectHotelWithoutAddress();
        shouldRejectDuplicateEmail();
        shouldIssueTemporaryPasswordAndInvalidateOldPassword();
        System.out.println("AuthServiceSelfTest: OK");
    }

    private static void shouldRegisterAndLoginCasaTemplate() {
        TestPublisher publisher = new TestPublisher();
        AuthService service = service(publisher);

        String userId = service.register(new RegisterUserCommand(
                "casa", "Ana", "Lopez", 28,
                "ana-selftest@example.com", "+525511110001", "+525511110001", "",
                "", "", "", "", "Secret123"
        ));
        assert userId != null && !userId.isBlank() : "expected userId";

        LoginResult login = service.login(new LoginCommand("ana-selftest@example.com", "Secret123"));
        assert login.authenticated() : "expected authenticated";
        assert "ok".equals(login.reason()) : "expected reason ok";
        assert publisher.hasTopic("portal/register") : "expected register event";
        assert publisher.hasTopic("portal/login") : "expected login event";
    }

    private static void shouldRejectHotelWithoutAddress() {
        AuthService service = service(new TestPublisher());
        try {
            service.register(new RegisterUserCommand(
                    "hotel", "H", "U", 25,
                    "hotel-selftest@example.com", "+525511110002", "+525511110002", "",
                    "", "", "", "", "Secret123"
            ));
            throw new IllegalStateException("expected address_required_hotel");
        } catch (IllegalArgumentException e) {
            assert "address_required_hotel".equals(e.getMessage()) : "unexpected error " + e.getMessage();
        }
    }

    private static void shouldRejectDuplicateEmail() {
        AuthService service = service(new TestPublisher());
        service.register(new RegisterUserCommand(
                "casa", "A", "B", 20,
                "dup-selftest@example.com", "+525511110003", "+525511110003", "",
                "", "", "", "", "Secret123"
        ));
        try {
            service.register(new RegisterUserCommand(
                    "casa", "C", "D", 21,
                    "dup-selftest@example.com", "+525511110004", "+525511110004", "",
                    "", "", "", "", "Secret123"
            ));
            throw new IllegalStateException("expected email_already_exists");
        } catch (IllegalArgumentException e) {
            assert "email_already_exists".equals(e.getMessage()) : "unexpected error " + e.getMessage();
        }
    }

    private static void shouldIssueTemporaryPasswordAndInvalidateOldPassword() {
        TestEmailSender emailSender = new TestEmailSender();
        AuthService service = service(new TestPublisher(), emailSender);

        service.register(new RegisterUserCommand(
                "casa", "Tmp", "Pwd", 33,
                "tmp-selftest@example.com", "+525511110005", "+525511110005", "",
                "", "", "", "", "Initial123"
        ));

        String temporaryPassword = service.issueTemporaryPassword("tmp-selftest@example.com");
        assert temporaryPassword != null && !temporaryPassword.isBlank() : "expected temp password";
        assert temporaryPassword.equals(emailSender.lastPassword) : "expected email with temp password";

        LoginResult oldLogin = service.login(new LoginCommand("tmp-selftest@example.com", "Initial123"));
        assert !oldLogin.authenticated() : "old password should fail";

        LoginResult newLogin = service.login(new LoginCommand("tmp-selftest@example.com", temporaryPassword));
        assert newLogin.authenticated() : "temp password should work";
    }

    private static AuthService service(TestPublisher publisher) {
        return service(publisher, new TestEmailSender());
    }

    private static AuthService service(TestPublisher publisher, TestEmailSender emailSender) {
        return new AuthService(
                new InMemoryUserRepository(),
                new Sha256PasswordHasher(),
                publisher,
                emailSender,
                new NoopOpenWrtGateway(),
                3600
        );
    }

    private static final class TestPublisher implements AsyncEventPublisher {
        private final List<String> topics = new ArrayList<>();

        @Override
        public void publish(String topic, String payload) {
            topics.add(topic);
        }

        boolean hasTopic(String topic) {
            return topics.contains(topic);
        }
    }

    private static final class TestEmailSender implements EmailSender {
        private String lastPassword;

        @Override
        public void sendTemporaryPassword(String destinationEmail, String generatedPassword) {
            this.lastPassword = generatedPassword;
        }
    }

    private static final class NoopOpenWrtGateway implements OpenWrtAccessGateway {
        @Override
        public void allowSession(String userId, int ttlSeconds) {
        }
    }
}
