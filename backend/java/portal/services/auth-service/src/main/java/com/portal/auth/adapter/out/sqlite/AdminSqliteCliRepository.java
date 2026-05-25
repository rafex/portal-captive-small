package com.portal.auth.adapter.out.sqlite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdminSqliteCliRepository {
    private final String dbPath;

    public AdminSqliteCliRepository(String dbPath) {
        this.dbPath = dbPath;
        initSchema();
    }

    public Optional<AdminUser> findByUsername(String username) {
        List<String> rows = query("SELECT username,password_hash,password_salt,role,enabled,created_at,updated_at " +
                "FROM admin_users WHERE username=" + q(username) + " LIMIT 1");
        if (rows.isEmpty() || rows.get(0).isBlank()) {
            return Optional.empty();
        }
        String[] p = rows.get(0).split("\\|", -1);
        if (p.length < 7) {
            return Optional.empty();
        }
        return Optional.of(new AdminUser(
                p[0],
                p[1],
                p[2],
                p[3],
                "1".equals(p[4]) || "true".equalsIgnoreCase(p[4]),
                Instant.parse(p[5]),
                Instant.parse(p[6])
        ));
    }

    public void saveAdmin(String username, String passwordHash, String passwordSalt, String role, boolean enabled) {
        String now = Instant.now().toString();
        execute("INSERT INTO admin_users (username,password_hash,password_salt,role,enabled,created_at,updated_at) VALUES (" +
                q(username) + "," + q(passwordHash) + "," + q(passwordSalt) + "," + q(role) + "," + (enabled ? "1" : "0") + "," + q(now) + "," + q(now) + ")" +
                " ON CONFLICT(username) DO UPDATE SET " +
                "password_hash=excluded.password_hash,password_salt=excluded.password_salt,role=excluded.role,enabled=excluded.enabled,updated_at=excluded.updated_at");
    }

    public List<RegisteredUserRow> listRegisteredUsers(int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        String sql = "SELECT u.id,u.created_at,u.updated_at,p.profile_json " +
                "FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id " +
                "ORDER BY u.updated_at DESC LIMIT " + capped;
        List<String> rows = query(sql);
        List<RegisteredUserRow> out = new ArrayList<>();
        for (String row : rows) {
            if (row == null || row.isBlank()) continue;
            String[] p = row.split("\\|", -1);
            if (p.length < 4) continue;
            out.add(new RegisteredUserRow(p[0], p[1], p[2], p[3] == null ? "{}" : p[3]));
        }
        return out;
    }

    private void initSchema() {
        execute("CREATE TABLE IF NOT EXISTS admin_users (" +
                "username TEXT PRIMARY KEY," +
                "password_hash TEXT NOT NULL," +
                "password_salt TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "enabled INTEGER NOT NULL DEFAULT 1," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ");");
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

    private static String q(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("'", "''") + "'";
    }

    public record AdminUser(
            String username,
            String passwordHash,
            String passwordSalt,
            String role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record RegisteredUserRow(
            String userId,
            String createdAt,
            String updatedAt,
            String profileJson
    ) {
    }
}

