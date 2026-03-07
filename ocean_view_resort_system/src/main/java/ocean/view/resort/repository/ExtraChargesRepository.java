package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.ExtraCharge;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ExtraChargesRepository {

    private static ExtraCharge mapRow(ResultSet rs) throws java.sql.SQLException {
        ExtraCharge e = new ExtraCharge();
        e.setId(rs.getInt("id"));
        e.setReservationId(rs.getString("reservation_id"));
        e.setDescription(rs.getString("description"));
        e.setAmount(rs.getBigDecimal("amount"));
        e.setChargedAt(rs.getObject("charged_at", java.time.LocalDateTime.class));
        e.setCreatedBy(rs.getString("created_by"));
        return e;
    }

    public List<ExtraCharge> findByReservationId(String reservationId) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<ExtraCharge> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM extra_charges WHERE reservation_id = ? ORDER BY charged_at")) {
                ps.setString(1, reservationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public void save(String reservationId, String description, BigDecimal amount, String createdBy) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO extra_charges (reservation_id, description, amount, charged_at, created_by) VALUES (?,?,?,NOW(),?)")) {
                ps.setString(1, reservationId);
                ps.setString(2, description);
                ps.setBigDecimal(3, amount);
                ps.setString(4, createdBy);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void delete(int id) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM extra_charges WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
