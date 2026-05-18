package com.portal.auth.adapter.out.notifications;

import com.portal.auth.application.port.out.EmailSender;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class SmtpSocketEmailSender implements EmailSender {
    private final String host;
    private final int port;
    private final String from;

    public SmtpSocketEmailSender(String host, int port, String from) {
        this.host = host;
        this.port = port;
        this.from = from;
    }

    @Override
    public void sendTemporaryPassword(String destinationEmail, String generatedPassword) {
        String body = "Tu clave temporal es: " + generatedPassword;
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            read(reader);
            send(writer, "HELO localhost");
            read(reader);
            send(writer, "MAIL FROM:<" + from + ">");
            read(reader);
            send(writer, "RCPT TO:<" + destinationEmail + ">");
            read(reader);
            send(writer, "DATA");
            read(reader);
            send(writer, "Subject: Clave temporal portal cautivo");
            send(writer, "From: " + from);
            send(writer, "To: " + destinationEmail);
            send(writer, "");
            send(writer, body);
            send(writer, ".");
            read(reader);
            send(writer, "QUIT");
        } catch (IOException e) {
            throw new IllegalStateException("smtp_send_failed", e);
        }
    }

    private static String read(BufferedReader reader) throws IOException {
        return reader.readLine();
    }

    private static void send(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }
}
