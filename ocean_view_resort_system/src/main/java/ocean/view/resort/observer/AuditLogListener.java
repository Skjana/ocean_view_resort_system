package ocean.view.resort.observer;

import ocean.view.resort.manager.DatabaseManager;

import java.sql.PreparedStatement;
import java.util.Map;

public class AuditLogListener {

    private final EventBus eventBus;

    public AuditLogListener(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void init() {
        eventBus.on("LOGIN", this::onLogin);
    }

    @SuppressWarnings("unchecked")
    private void onLogin(String event, Object data) {
        if (!(data instanceof Map)) return;
        Map<String, Object> m = (Map<String, Object>) data;
        String userId = (String) m.get("userId");
        String username = (String) m.get("username");
        if (userId == null) userId = username != null ? username : "unknown";
        try {
            String finalUserId = userId;
            DatabaseManager.getInstance().execute(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO audit_log (action, table_name, record_id, user_id, detail) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, "LOGIN");
                    ps.setString(2, "users");
                    ps.setString(3, finalUserId);
                    ps.setString(4, finalUserId);
                    ps.setString(5, "Login: " + username);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception ignored) { }
    }
}
