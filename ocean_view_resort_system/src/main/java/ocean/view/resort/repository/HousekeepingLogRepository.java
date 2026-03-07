package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.HousekeepingLogEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HousekeepingLogRepository {

    private static HousekeepingLogEntry mapRow(ResultSet rs) throws java.sql.SQLException {
        HousekeepingLogEntry e = new HousekeepingLogEntry();
        e.setId(rs.getInt("id"));
        e.setRoomId(rs.getString("room_id"));
        e.setStatus(rs.getString("status"));
        e.setNotedAt(rs.getObject("noted_at", java.time.LocalDateTime.class));
        e.setStaffId(rs.getString("staff_id"));
        e.setNotes(rs.getString("notes"));
        return e;
    }

    public List<HousekeepingLogEntry> findByRoomId(String roomId) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<HousekeepingLogEntry> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM housekeeping_log WHERE room_id = ? ORDER BY noted_at DESC")) {
                ps.setString(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public void log(String roomId, String status, String staffId, String notes) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO housekeeping_log (room_id, status, noted_at, staff_id, notes) VALUES (?,?,NOW(),?,?)")) {
                ps.setString(1, roomId);
                ps.setString(2, status);
                ps.setString(3, staffId);
                ps.setString(4, notes);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET room_status = ? WHERE id = ?")) {
                ps.setString(1, "BACK_IN_SERVICE".equals(status) ? "AVAILABLE" : status);
                ps.setString(2, roomId);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
