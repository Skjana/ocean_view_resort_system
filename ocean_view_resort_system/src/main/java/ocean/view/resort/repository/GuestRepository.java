package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.Guest;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GuestRepository {

    private static Guest mapRow(ResultSet rs) throws java.sql.SQLException {
        Guest g = new Guest();
        g.setId(rs.getInt("id"));
        g.setFullName(rs.getString("full_name"));
        g.setEmail(rs.getString("email"));
        g.setPhone(rs.getString("phone"));
        g.setAddress(rs.getString("address"));
        g.setNationality(rs.getString("nationality"));
        g.setNotes(rs.getString("notes"));
        g.setCreatedAt(rs.getObject("created_at", java.time.LocalDateTime.class));
        return g;
    }

    public List<Guest> findAll() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Guest> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guests ORDER BY full_name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        });
    }

    public Optional<Guest> findById(int id) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guests WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Guest> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guests WHERE email = ?")) {
                ps.setString(1, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<Guest> searchByNameOrEmail(String query) {
        if (query == null || query.isBlank()) return findAll();
        return DatabaseManager.getInstance().execute(conn -> {
            List<Guest> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM guests WHERE LOWER(full_name) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?) ORDER BY full_name")) {
                String q = "%" + query + "%";
                ps.setString(1, q);
                ps.setString(2, q);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public Guest save(Guest g) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO guests (full_name, email, phone, address, nationality, notes, created_at) VALUES (?,?,?,?,?,?,NOW())",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, g.getFullName());
                ps.setString(2, g.getEmail());
                ps.setString(3, g.getPhone());
                ps.setString(4, g.getAddress());
                ps.setString(5, g.getNationality());
                ps.setString(6, g.getNotes());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) g.setId(keys.getInt(1));
                }
            }
            return g;
        });
    }

    public void update(Guest g) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE guests SET full_name=?, email=?, phone=?, address=?, nationality=?, notes=? WHERE id=?")) {
                ps.setString(1, g.getFullName());
                ps.setString(2, g.getEmail());
                ps.setString(3, g.getPhone());
                ps.setString(4, g.getAddress());
                ps.setString(5, g.getNationality());
                ps.setString(6, g.getNotes());
                ps.setInt(7, g.getId());
                ps.executeUpdate();
            }
            return null;
        });
    }
}