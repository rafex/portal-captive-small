package com.portal.auth.adapter.out.sqlite;

import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteCliUserRepository implements UserRepository {
    private final String dbPath;

    public SqliteCliUserRepository(String dbPath) {
        this.dbPath = dbPath;
        initSchema();
    }

    @Override
    public void save(User user) {
        String sql = "INSERT INTO users (" +
                "user_id, first_name, last_name, age, email, phone, mobile, address_text, " +
                "social_facebook, social_instagram, social_tiktok, social_x, " +
                "password_hash, password_salt, created_at, updated_at" +
                ") VALUES (" +
                q(user.userId()) + "," + q(user.firstName()) + "," + q(user.lastName()) + "," + q(user.age()) + "," +
                q(user.email()) + "," + q(user.phone()) + "," + q(user.mobile()) + "," + q(user.address()) + "," +
                q(user.socialFacebook()) + "," + q(user.socialInstagram()) + "," + q(user.socialTiktok()) + "," + q(user.socialX()) + "," +
                q(user.passwordHash()) + "," + q(user.passwordSalt()) + "," + q(user.createdAt().toString()) + "," + q(user.updatedAt().toString()) +
                ") ON CONFLICT(user_id) DO UPDATE SET " +
                "first_name=excluded.first_name,last_name=excluded.last_name,age=excluded.age,email=excluded.email," +
                "phone=excluded.phone,mobile=excluded.mobile,address_text=excluded.address_text," +
                "social_facebook=excluded.social_facebook,social_instagram=excluded.social_instagram," +
                "social_tiktok=excluded.social_tiktok,social_x=excluded.social_x,password_hash=excluded.password_hash," +
                "password_salt=excluded.password_salt,updated_at=excluded.updated_at";
        execute(sql);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findOne("SELECT user_id,first_name,last_name,age,email,phone,mobile,address_text,social_facebook,social_instagram,social_tiktok,social_x,password_hash,password_salt,created_at,updated_at FROM users WHERE email=" + q(email) + " LIMIT 1");
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return findOne("SELECT user_id,first_name,last_name,age,email,phone,mobile,address_text,social_facebook,social_instagram,social_tiktok,social_x,password_hash,password_salt,created_at,updated_at FROM users WHERE phone=" + q(phone) + " LIMIT 1");
    }

    private Optional<User> findOne(String sql) {
        List<String> lines = query(sql);
        if (lines.isEmpty() || lines.get(0).isBlank()) {
            return Optional.empty();
        }
        String[] p = lines.get(0).split("\\|", -1);
        if (p.length < 16) {
            return Optional.empty();
        }
        return Optional.of(new User(
                p[0], p[1], p[2], parseInt(p[3]), nullIfEmpty(p[4]), nullIfEmpty(p[5]), nullIfEmpty(p[6]),
                nullIfEmpty(p[7]), nullIfEmpty(p[8]), nullIfEmpty(p[9]), nullIfEmpty(p[10]), nullIfEmpty(p[11]),
                p[12], p[13], Instant.parse(p[14]), Instant.parse(p[15])
        ));
    }

    private void initSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id TEXT PRIMARY KEY," +
                "first_name TEXT NOT NULL," +
                "last_name TEXT NOT NULL," +
                "age INTEGER," +
                "email TEXT UNIQUE," +
                "phone TEXT UNIQUE," +
                "mobile TEXT," +
                "address_text TEXT," +
                "social_facebook TEXT," +
                "social_instagram TEXT," +
                "social_tiktok TEXT," +
                "social_x TEXT," +
                "password_hash TEXT NOT NULL," +
                "password_salt TEXT NOT NULL," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ");";
        execute(sql);
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

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private static String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static String q(Object value) {
        if (value == null) {
            return "NULL";
        }
        String v = value.toString().replace("'", "''");
        return "'" + v + "'";
    }
}
