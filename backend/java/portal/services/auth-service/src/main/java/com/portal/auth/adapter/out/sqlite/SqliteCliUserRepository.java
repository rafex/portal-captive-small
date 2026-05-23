package com.portal.auth.adapter.out.sqlite;

import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;
import com.portal.auth.shared.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteCliUserRepository implements UserRepository {
    private final String dbPath;
    private final int usersTtlSeconds;

    public SqliteCliUserRepository(String dbPath) {
        this.dbPath = dbPath;
        this.usersTtlSeconds = parseTtlSeconds(System.getenv("DB_USER_TTL_SECONDS"));
        initSchema();
    }

    @Override
    public void save(User user) {
        cleanupExpired();

        String usersSql = "INSERT INTO users (" +
                "id, password_hash, password_salt, created_at, updated_at" +
                ") VALUES (" +
                q(user.userId()) + "," + q(nullIfBlank(user.passwordHash())) + "," + q(nullIfBlank(user.passwordSalt())) + "," +
                q(user.createdAt().toString()) + "," + q(user.updatedAt().toString()) +
                ") ON CONFLICT(id) DO UPDATE SET " +
                "password_hash=excluded.password_hash,password_salt=excluded.password_salt,updated_at=excluded.updated_at";
        execute(usersSql);

        String profileJson = "{" +
                qk("template") + ":" + jv(user.template()) + "," +
                qk("deviceIp") + ":" + jv(user.deviceIp()) + "," +
                qk("deviceUuid") + ":" + jv(user.deviceUuid()) + "," +
                qk("deviceFingerprint") + ":" + jv(user.deviceFingerprint()) + "," +
                qk("userAgent") + ":" + jv(user.userAgent()) + "," +
                qk("firstName") + ":" + jv(user.firstName()) + "," +
                qk("lastName") + ":" + jv(user.lastName()) + "," +
                qk("age") + ":" + qn(user.age()) + "," +
                qk("email") + ":" + jv(user.email()) + "," +
                qk("phone") + ":" + jv(user.phone()) + "," +
                qk("mobile") + ":" + jv(user.mobile()) + "," +
                qk("address") + ":" + jv(user.address()) + "," +
                qk("socialFacebook") + ":" + jv(user.socialFacebook()) + "," +
                qk("socialInstagram") + ":" + jv(user.socialInstagram()) + "," +
                qk("socialTiktok") + ":" + jv(user.socialTiktok()) + "," +
                qk("socialX") + ":" + jv(user.socialX()) +
                "}";

        String profileSql = "INSERT INTO user_profiles (user_id, profile_json, created_at, updated_at) VALUES (" +
                q(user.userId()) + "," + q(profileJson) + "," + q(user.createdAt().toString()) + "," + q(user.updatedAt().toString()) +
                ") ON CONFLICT(user_id) DO UPDATE SET profile_json=excluded.profile_json,updated_at=excluded.updated_at";
        execute(profileSql);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        cleanupExpired();
        return findOne("SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json " +
                "FROM users u " +
                "LEFT JOIN user_profiles p ON p.user_id=u.id " +
                "WHERE json_extract(p.profile_json, '$.email')=" + q(email) + " LIMIT 1");
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        cleanupExpired();
        return findOne("SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json " +
                "FROM users u " +
                "LEFT JOIN user_profiles p ON p.user_id=u.id " +
                "WHERE json_extract(p.profile_json, '$.phone')=" + q(phone) + " LIMIT 1");
    }

    @Override
    public Optional<User> findByDeviceIp(String deviceIp) {
        cleanupExpired();
        return findOne("SELECT u.id, u.password_hash, u.password_salt, u.created_at, u.updated_at, p.profile_json " +
                "FROM users u " +
                "LEFT JOIN user_profiles p ON p.user_id=u.id " +
                "WHERE json_extract(p.profile_json, '$.deviceIp')=" + q(deviceIp) + " LIMIT 1");
    }

    private Optional<User> findOne(String sql) {
        List<String> lines = query(sql);
        if (lines.isEmpty() || lines.get(0).isBlank()) {
            return Optional.empty();
        }
        String[] p = lines.get(0).split("\\|", -1);
        if (p.length < 6) {
            return Optional.empty();
        }

        Map<String, String> profile = SimpleJson.parseFlatObject(nullIfEmpty(p[5]) == null ? "{}" : p[5]);

        return Optional.of(new User(
                p[0],
                profile.getOrDefault("template", "hotel"),
                profile.get("deviceIp"),
                profile.get("deviceUuid"),
                profile.get("deviceFingerprint"),
                profile.get("userAgent"),
                profile.getOrDefault("firstName", ""),
                profile.getOrDefault("lastName", ""),
                parseInt(profile.get("age")),
                profile.get("email"),
                profile.get("phone"),
                profile.get("mobile"),
                profile.get("address"),
                profile.get("socialFacebook"),
                profile.get("socialInstagram"),
                profile.get("socialTiktok"),
                profile.get("socialX"),
                nullIfEmpty(p[1]) == null ? "" : p[1],
                nullIfEmpty(p[2]) == null ? "" : p[2],
                Instant.parse(p[3]),
                Instant.parse(p[4])
        ));
    }

    private void initSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id TEXT PRIMARY KEY," +
                "password_hash TEXT," +
                "password_salt TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ");" +
                "CREATE TABLE IF NOT EXISTS user_profiles (" +
                "user_id TEXT PRIMARY KEY," +
                "profile_json TEXT NOT NULL," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ");";
        execute(sql);
    }

    private void cleanupExpired() {
        if (usersTtlSeconds <= 0) {
            return;
        }
        String prune = "DELETE FROM users WHERE updated_at < datetime('now', '-" + usersTtlSeconds + " seconds')";
        execute(prune);
    }

    private void execute(String sql) {
        runSql(sql, false);
    }

    private List<String> query(String sql) {
        return runSql(sql, true);
    }

    private List<String> runSql(String sql, boolean captureStdout) {
        ProcessBuilder pb = new ProcessBuilder("sqlite3", "-separator", "|", dbPath, sql);
        try {
            Process p = pb.start();
            String err = readAll(p.getErrorStream());
            String out = captureStdout ? readAll(p.getInputStream()) : "";
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException("sqlite3_exit=" + code + " err=" + err);
            }
            List<String> lines = new ArrayList<>();
            if (!out.isBlank()) {
                for (String line : out.split("\\R")) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sqlite3_execution_failed", e);
        }
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static int parseTtlSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return 3600;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 3600;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private static String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    private static String qk(String key) {
        return "\"" + key + "\"";
    }

    private static String jv(String value) {
        if (value == null) {
            return "null";
        }
        String v = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + v + "\"";
    }

    private static String qn(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String q(Object value) {
        if (value == null) {
            return "NULL";
        }
        String v = value.toString().replace("'", "''");
        return "'" + v + "'";
    }
}
