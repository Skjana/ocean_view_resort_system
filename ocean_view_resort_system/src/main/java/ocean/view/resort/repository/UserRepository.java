package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserRepository {

    public Optional<Map<String, Object>> findByUsernameAndHash(String username, String passwordHash) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, username, password_hash, role, full_name, email, is_active, created_at FROM users WHERE username = ? AND password_hash = ? AND is_active = 1")) {
                ps.setString(1, username);
                ps.setString(2, passwordHash);
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

}
