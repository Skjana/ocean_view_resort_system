package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.Room;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoomRepository {

    private static Room mapRow(ResultSet rs) throws java.sql.SQLException {
        Room r = new Room();
        r.setId(rs.getString("id"));
        r.setRoomNumber(rs.getString("room_number"));
        r.setRoomType(rs.getString("room_type"));
        r.setFloor(rs.getInt("floor"));
        r.setMaxOccupancy(rs.getInt("max_occupancy"));
        r.setNightlyRate(rs.getBigDecimal("nightly_rate"));
        r.setViewDesc(rs.getString("view_desc"));
        r.setAmenitiesStr(rs.getString("amenities"));
        r.setAvailable(rs.getObject("is_available", Integer.class) != null && rs.getInt("is_available") == 1);
        try { r.setRoomStatus(rs.getString("room_status")); } catch (Exception ignored) { }
        return r;
    }

    /** Update room status (AVAILABLE, CLEANING, OUT_OF_ORDER). */
    public void updateRoomStatus(String roomId, String status) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET room_status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setString(2, roomId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<Room> findAll() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Room> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM rooms ORDER BY room_number");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        });
    }

    public List<Room> findAvailableByTypeAndDates(String roomType, LocalDate checkIn, LocalDate checkOut) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Room> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("CALL sp_check_room_availability(?, ?, ?)")) {
                ps.setString(1, roomType);
                ps.setObject(2, checkIn);
                ps.setObject(3, checkOut);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public Optional<Room> findById(String id) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM rooms WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }
}
