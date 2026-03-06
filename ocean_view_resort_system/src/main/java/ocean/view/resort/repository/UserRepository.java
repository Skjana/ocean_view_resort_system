package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class UserRepository {

    public Optional<Map<String, Object>> findByUsername(String username) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, password_hash, role, full_name, email, is_active, created_at FROM users WHERE username = ? AND is_active = 1")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("username", rs.getString("username"));
                    m.put("password_hash", rs.getString("password_hash"));
                    m.put("role", rs.getString("role"));
                    m.put("full_name", rs.getString("full_name"));
                    m.put("email", rs.getString("email"));
                    m.put("is_active", rs.getObject("is_active"));
                    m.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
                    return Optional.of(m);
                }
            }
        });
    }

    public Optional<Map<String, Object>> findByUsernameAndHash(String username, String passwordHash) {
        if (username == null || passwordHash == null) return Optional.empty();
        String hash = passwordHash.trim();
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, password_hash, role, full_name, email, is_active, created_at FROM users WHERE username = ? AND TRIM(password_hash) = ? AND is_active = 1")) {
                ps.setString(1, username.trim());
                ps.setString(2, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("username", rs.getString("username"));
                    m.put("password_hash", rs.getString("password_hash"));
                    m.put("role", rs.getString("role"));
                    m.put("full_name", rs.getString("full_name"));
                    m.put("email", rs.getString("email"));
                    m.put("is_active", rs.getObject("is_active"));
                    m.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
                    return Optional.of(m);
                }
            }
        });
    }

    public List<Map<String, Object>> findAllUsers() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Map<String, Object>> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, role, full_name, email, is_active, created_at FROM users WHERE is_active = 1 ORDER BY created_at");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("username", rs.getString("username"));
                    m.put("role", rs.getString("role"));
                    m.put("full_name", rs.getString("full_name"));
                    m.put("email", rs.getString("email"));
                    m.put("is_active", rs.getObject("is_active"));
                    m.put("created_at", rs.getTimestamp("created_at"));
                    list.add(m);
                }
            }
            return list;
        });
    }

    public Optional<Map<String, Object>> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, role, full_name, email, is_active, created_at FROM users WHERE id = ?")) {
                ps.setString(1, id.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("username", rs.getString("username"));
                    m.put("role", rs.getString("role"));
                    m.put("full_name", rs.getString("full_name"));
                    m.put("email", rs.getString("email"));
                    m.put("is_active", rs.getObject("is_active"));
                    m.put("created_at", rs.getTimestamp("created_at"));
                    return Optional.of(m);
                }
            }
        });
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) return false;
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public String getNextUserId() {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(MAX(CAST(SUBSTRING(id, 2) AS UNSIGNED)), 0) + 1 AS n FROM users WHERE id REGEXP '^U[0-9]+$'");
                 ResultSet rs = ps.executeQuery()) {
                int n = rs.next() ? rs.getInt("n") : 1;
                return "U" + String.format("%03d", n);
            }
        });
    }

    public void save(String id, String username, String passwordHash, String role, String fullName, String email) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, username, password_hash, role, full_name, email, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW())")) {
                ps.setString(1, id);
                ps.setString(2, username.trim());
                ps.setString(3, passwordHash);
                ps.setString(4, role != null ? role : "STAFF");
                ps.setString(5, fullName != null ? fullName.trim() : "");
                ps.setString(6, email != null ? email.trim() : null);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void update(String id, String fullName, String email, String role, String newPasswordHash) {
        if (newPasswordHash != null && !newPasswordHash.isBlank()) {
            DatabaseManager.getInstance().execute(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET full_name = ?, email = ?, role = ?, password_hash = ? WHERE id = ?")) {
                    ps.setString(1, fullName != null ? fullName.trim() : "");
                    ps.setString(2, email != null ? email.trim() : null);
                    ps.setString(3, role != null ? role : "STAFF");
                    ps.setString(4, newPasswordHash);
                    ps.setString(5, id);
                    ps.executeUpdate();
                }
                return null;
            });
        } else {
            DatabaseManager.getInstance().execute(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET full_name = ?, email = ?, role = ? WHERE id = ?")) {
                    ps.setString(1, fullName != null ? fullName.trim() : "");
                    ps.setString(2, email != null ? email.trim() : null);
                    ps.setString(3, role != null ? role : "STAFF");
                    ps.setString(4, id);
                    ps.executeUpdate();
                }
                return null;
            });
        }
    }

    public void deactivate(String id) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_active = 0 WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public int countActiveAdministrators() {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS c FROM users WHERE role = 'ADMINISTRATOR' AND is_active = 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("c") : 0;
            }
        });
    }
}
