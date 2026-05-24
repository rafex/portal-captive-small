package com.portal.auth.adapter.out.mqtt;

import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;
import com.portal.auth.shared.SimpleJson;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MqttRustUserRepository implements UserRepository, AutoCloseable {
    private final int responseWaitSeconds;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final MqttCliRpcClient rpcClient;

    public MqttRustUserRepository(String host,
                                  int port,
                                  String requestTopic,
                                  int responseWaitSeconds,
                                  int maxRetries,
                                  long retryBackoffMs) {
        this.responseWaitSeconds = responseWaitSeconds;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(10L, retryBackoffMs);
        this.rpcClient = new MqttCliRpcClient(host, port, requestTopic, "portal/db/user/response/#");
    }

    public boolean isHealthy() {
        return rpcClient.isHealthy();
    }

    @Override
    public void save(User user) {
        String requestId = UUID.randomUUID().toString();
        String payload = "{" +
                q("requestId") + ":" + qv(requestId) + "," +
                q("op") + ":" + qv("user_save") + "," +
                q("replyTopic") + ":" + qv(replyTopic(requestId)) + "," +
                q("userId") + ":" + qv(user.userId()) + "," +
                q("template") + ":" + qv(user.template()) + "," +
                q("deviceIp") + ":" + qv(user.deviceIp()) + "," +
                q("deviceUuid") + ":" + qv(user.deviceUuid()) + "," +
                q("deviceFingerprint") + ":" + qv(user.deviceFingerprint()) + "," +
                q("userAgent") + ":" + qv(user.userAgent()) + "," +
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

        String response = callWithRetry("user_save", payload, requestId);
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

    @Override
    public Optional<User> findByDeviceIp(String deviceIp) {
        return findBy("user_find_device_ip", "deviceIp", deviceIp);
    }

    private Optional<User> findBy(String op, String field, String value) {
        String requestId = UUID.randomUUID().toString();
        String payload = "{" +
                q("requestId") + ":" + qv(requestId) + "," +
                q("op") + ":" + qv(op) + "," +
                q("replyTopic") + ":" + qv(replyTopic(requestId)) + "," +
                q(field) + ":" + qv(value) +
                "}";

        String response = callWithRetry(op, payload, requestId);
        Map<String, String> json = SimpleJson.parseFlatObject(response);
        if (!"ok".equals(json.get("status"))) {
            throw new IllegalStateException("db_read_failed " + json.getOrDefault("error", "unknown"));
        }
        if ("false".equals(json.get("found"))) {
            return Optional.empty();
        }
        return Optional.of(new User(
                json.get("userId"),
                json.getOrDefault("template", "hotel"),
                json.get("deviceIp"),
                json.get("deviceUuid"),
                json.get("deviceFingerprint"),
                json.get("userAgent"),
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
                parseInstantOrNow(json.get("createdAt")),
                parseInstantOrNow(json.get("updatedAt"))
        ));
    }

    private String callWithRetry(String op, String payload, String requestId) {
        long startNs = System.nanoTime();
        RuntimeException last = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long attemptStartNs = System.nanoTime();
            try {
                String response = rpcClient.request(requestId, payload, responseWaitSeconds);
                long attemptMs = (System.nanoTime() - attemptStartNs) / 1_000_000;
                long totalMs = (System.nanoTime() - startNs) / 1_000_000;
                DbMqttMetrics.recordSuccess(totalMs, attempt);
                System.out.println("db_mqtt_rpc op=" + op + " requestId=" + requestId +
                        " status=ok attempt=" + (attempt + 1) + " attempt_ms=" + attemptMs +
                        " total_ms=" + totalMs);
                return response;
            } catch (RuntimeException ex) {
                last = ex;
                long attemptMs = (System.nanoTime() - attemptStartNs) / 1_000_000;
                System.err.println("db_mqtt_rpc op=" + op + " requestId=" + requestId +
                        " status=error attempt=" + (attempt + 1) + " attempt_ms=" + attemptMs +
                        " error=" + sanitize(ex.getMessage()));

                if (attempt >= maxRetries) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        long totalMs = (System.nanoTime() - startNs) / 1_000_000;
        DbMqttMetrics.recordError(totalMs, maxRetries);
        throw new IllegalStateException("db_mqtt_rpc_exhausted op=" + op + " requestId=" + requestId, last);
    }

    private void sleepBackoff(int attempt) {
        long factor = 1L << Math.min(attempt, 10);
        long sleepMs = Math.min(retryBackoffMs * factor, 5_000L);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mqtt_retry_interrupted", e);
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

    private static Instant parseInstantOrNow(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "unknown";
        }
        return raw.replace("\"", "'");
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

    @Override
    public void close() {
        rpcClient.close();
    }
}
