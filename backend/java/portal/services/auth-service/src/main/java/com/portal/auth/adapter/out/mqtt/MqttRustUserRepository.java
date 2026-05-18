package com.portal.auth.adapter.out.mqtt;

import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;
import com.portal.auth.shared.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MqttRustUserRepository implements UserRepository {
    private final String host;
    private final int port;
    private final String requestTopic;
    private final int responseWaitSeconds;

    public MqttRustUserRepository(String host, int port, String requestTopic, int responseWaitSeconds) {
        this.host = host;
        this.port = port;
        this.requestTopic = requestTopic;
        this.responseWaitSeconds = responseWaitSeconds;
    }

    @Override
    public void save(User user) {
        String requestId = UUID.randomUUID().toString();
        String replyTopic = replyTopic(requestId);
        String payload = "{" +
                q("requestId") + ":" + qv(requestId) + "," +
                q("op") + ":" + qv("user_save") + "," +
                q("replyTopic") + ":" + qv(replyTopic) + "," +
                q("userId") + ":" + qv(user.userId()) + "," +
                q("firstName") + ":" + qv(user.firstName()) + "," +
                q("lastName") + ":" + qv(user.lastName()) + "," +
                q("age") + ":" + qn(user.age()) + "," +
                q("email") + ":" + qv(user.email()) + "," +
                q("phone") + ":" + qv(user.phone()) + "," +
                q("mobile") + ":" + qv(user.mobile()) + "," +
                q("address") + ":" + qv(user.address()) + "," +
                q("socialFacebook") + ":" + qv(user.socialFacebook()) + "," +
                q("socialInstagram") + ":" + qv(user.socialInstagram()) + "," +
                q("socialTiktok") + ":" + qv(user.socialTiktok()) + "," +
                q("socialX") + ":" + qv(user.socialX()) + "," +
                q("passwordHash") + ":" + qv(user.passwordHash()) + "," +
                q("passwordSalt") + ":" + qv(user.passwordSalt()) + "," +
                q("createdAt") + ":" + qv(user.createdAt().toString()) + "," +
                q("updatedAt") + ":" + qv(user.updatedAt().toString()) +
                "}";

        String response = call(payload, replyTopic);
        Map<String, String> json = SimpleJson.parseFlatObject(response);
        if (!"ok".equals(json.get("status"))) {
            throw new IllegalStateException("db_write_failed " + json.getOrDefault("error", "unknown"));
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findBy("user_find_email", "email", email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return findBy("user_find_phone", "phone", phone);
    }

    private Optional<User> findBy(String op, String field, String value) {
        String requestId = UUID.randomUUID().toString();
        String replyTopic = replyTopic(requestId);
        String payload = "{" +
                q("requestId") + ":" + qv(requestId) + "," +
                q("op") + ":" + qv(op) + "," +
                q("replyTopic") + ":" + qv(replyTopic) + "," +
                q(field) + ":" + qv(value) +
                "}";

        String response = call(payload, replyTopic);
        Map<String, String> json = SimpleJson.parseFlatObject(response);
        if (!"ok".equals(json.get("status"))) {
            throw new IllegalStateException("db_read_failed " + json.getOrDefault("error", "unknown"));
        }
        if ("false".equals(json.get("found"))) {
            return Optional.empty();
        }
        return Optional.of(new User(
                json.get("userId"),
                json.get("firstName"),
                json.get("lastName"),
                parseInt(json.get("age")),
                json.get("email"),
                json.get("phone"),
                json.get("mobile"),
                json.get("address"),
                json.get("socialFacebook"),
                json.get("socialInstagram"),
                json.get("socialTiktok"),
                json.get("socialX"),
                json.get("passwordHash"),
                json.get("passwordSalt"),
                Instant.parse(json.get("createdAt")),
                Instant.parse(json.get("updatedAt"))
        ));
    }

    private String call(String payload, String replyTopic) {
        Process sub = null;
        try {
            sub = new ProcessBuilder(
                    "mosquitto_sub", "-h", host, "-p", String.valueOf(port),
                    "-t", replyTopic, "-C", "1", "-W", String.valueOf(responseWaitSeconds)
            ).start();

            Process pub = new ProcessBuilder(
                    "mosquitto_pub", "-h", host, "-p", String.valueOf(port),
                    "-t", requestTopic, "-m", payload
            ).start();
            int pubCode = pub.waitFor();
            if (pubCode != 0) {
                throw new IllegalStateException("mqtt_pub_failed code=" + pubCode);
            }

            String response = readAll(sub.getInputStream()).trim();
            int subCode = sub.waitFor();
            if (subCode != 0 || response.isBlank()) {
                throw new IllegalStateException("mqtt_sub_timeout_or_error code=" + subCode);
            }
            return response;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mqtt_rpc_failed", e);
        } finally {
            if (sub != null) {
                sub.destroy();
            }
        }
    }

    private static String replyTopic(String requestId) {
        return "portal/db/user/response/" + requestId;
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    private static String q(String value) {
        return "\"" + value + "\"";
    }

    private static String qv(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String qn(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
