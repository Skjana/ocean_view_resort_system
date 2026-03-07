package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.MaintenanceIssue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MaintenanceRepository {

    private static MaintenanceIssue mapRow(ResultSet rs) throws java.sql.SQLException {
        MaintenanceIssue m = new MaintenanceIssue();
        m.setId(rs.getInt("id"));
        m.setRoomId(rs.getString("room_id"));
        m.setTitle(rs.getString("title"));
        m.setDescription(rs.getString("description"));
        m.setCategory(rs.getString("category"));
        m.setStatus(rs.getString("status"));
        m.setAssignedTo(rs.getString("assigned_to"));
        m.setReportedAt(rs.getObject("reported_at", java.time.LocalDateTime.class));
        m.setResolvedAt(rs.getObject("resolved_at", java.time.LocalDateTime.class));
        return m;
    }

    public List<MaintenanceIssue> findAll() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<MaintenanceIssue> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM maintenance_issues ORDER BY reported_at DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        });
    }

    public List<MaintenanceIssue> findByRoomId(String roomId) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<MaintenanceIssue> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM maintenance_issues WHERE room_id = ? ORDER BY reported_at DESC")) {
                ps.setString(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public List<MaintenanceIssue> findByStatus(String status) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<MaintenanceIssue> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM maintenance_issues WHERE status = ? ORDER BY reported_at DESC")) {
                ps.setString(1, status);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public Optional<MaintenanceIssue> findById(int id) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM maintenance_issues WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public MaintenanceIssue save(MaintenanceIssue m) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO maintenance_issues (room_id, title, description, category, status, assigned_to, reported_at) VALUES (?,?,?,?,?,?,NOW())",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, m.getRoomId());
                ps.setString(2, m.getTitle());
                ps.setString(3, m.getDescription());
                ps.setString(4, m.getCategory() != null ? m.getCategory() : "OTHER");
                ps.setString(5, m.getStatus() != null ? m.getStatus() : "OPEN");
                ps.setString(6, m.getAssignedTo());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) m.setId(keys.getInt(1));
                }
            }
            return m;
        });
    }

    public void updateStatus(int id, String status) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE maintenance_issues SET status = ?, resolved_at = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setObject(2, "DONE".equals(status) ? java.time.LocalDateTime.now() : null);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void updateAssignedTo(int id, String assignedTo) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE maintenance_issues SET assigned_to = ? WHERE id = ?")) {
                ps.setString(1, assignedTo);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }
}