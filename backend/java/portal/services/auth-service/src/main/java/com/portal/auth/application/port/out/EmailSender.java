package com.portal.auth.application.port.out;

public interface EmailSender {
    void sendTemporaryPassword(String destinationEmail, String generatedPassword);
}
