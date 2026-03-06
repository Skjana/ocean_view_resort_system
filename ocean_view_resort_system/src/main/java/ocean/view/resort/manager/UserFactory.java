package ocean.view.resort.manager;

import ocean.view.resort.model.User;

import java.time.Instant;
import java.util.Map;

public final class UserFactory {

    private UserFactory() {}

    public static User create(Map<String, Object> row) {
        User u = new User();
        u.setId((String) row.get("id"));
        u.setUsername((String) row.get("username"));
        u.setRole((String) row.get("role"));
        u.setFullName((String) row.get("full_name"));
        u.setEmail((String) row.get("email"));
        u.setActive(isActive(row.get("is_active")));
        Object ca = row.get("created_at");
        u.setCreatedAt(ca instanceof Instant ? (Instant) ca : null);

        if ("ADMINISTRATOR".equals(u.getRole())) {
            u.setAccessLevel(10);
            u.setCanManageUsers(true);
            u.setCanConfigureRates(true);
        } else {
            u.setAccessLevel(5);
            u.setCanManageUsers(false);
            u.setCanConfigureRates(false);
        }
        return u;
    }

    private static boolean isActive(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return false;
    }
}
