package ocean.view.resort.config;

import ocean.view.resort.model.User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStore {
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();

    public static String create(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        return token;
    }

    public static Optional<User> get(String token) {
        return Optional.ofNullable(sessions.get(token));
    }

    public static void remove(String token) {
        sessions.remove(token);
    }
}
