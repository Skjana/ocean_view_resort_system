package ocean.view.resort.HotelService;

import ocean.view.resort.manager.UserFactory;
import ocean.view.resort.model.Room;
import ocean.view.resort.model.User;
import ocean.view.resort.observer.EventBus;
import ocean.view.resort.repository.ReservationRepository;
import ocean.view.resort.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HotelService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final EventBus eventBus;

    public HotelService(UserRepository userRepository, ReservationRepository reservationRepository,
                        EventBus eventBus) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.eventBus = eventBus;
    }

    public Optional<User> authenticate(String username, String password) {
        String hash = sha256Hex(password);
        Optional<Map<String, Object>> row = userRepository.findByUsernameAndHash(username, hash);
        if (row.isEmpty()) {
            eventBus.emit("notification", Map.of("type", "error", "msg", "Invalid username or password."));
            return Optional.empty();
        }
        User user = UserFactory.create(row.get());
        eventBus.emit("notification", Map.of("type", "success", "msg", "Welcome back, " + user.getFullName() + "!"));
        eventBus.emit("LOGIN", Map.of("userId", user.getId(), "username", username));
        return Optional.of(user);
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public Optional<User> getUserByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return userRepository.findByUsername(username.trim()).map(UserFactory::create);
    }


}
