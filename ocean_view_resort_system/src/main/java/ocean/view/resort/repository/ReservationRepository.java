package ocean.view.resort.repository;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.Reservation;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservationRepository {

    private static Reservation mapRow(ResultSet rs) throws java.sql.SQLException {
        Reservation r = new Reservation();
        r.setId(rs.getString("id"));
        r.setGuestName(rs.getString("guest_name"));
        r.setGuestAddress(rs.getString("guest_address"));
        r.setContactNumber(rs.getString("contact_number"));
        r.setEmail(rs.getString("email"));
        r.setNationality(rs.getString("nationality"));
        r.setRoomId(rs.getString("room_id"));
        r.setCheckInDate(rs.getObject("check_in_date", LocalDate.class));
        r.setCheckOutDate(rs.getObject("check_out_date", LocalDate.class));
        r.setNights(rs.getInt("nights"));
        r.setStatus(rs.getString("status"));
        r.setSpecialRequests(rs.getString("special_requests"));
        r.setSubTotal(rs.getBigDecimal("sub_total"));
        r.setTaxAmount(rs.getBigDecimal("tax_amount"));
        r.setDiscount(rs.getBigDecimal("discount"));
        r.setTotalAmount(rs.getBigDecimal("total_amount"));
        r.setLoyalty(rs.getObject("is_loyalty", Integer.class) != null && rs.getInt("is_loyalty") == 1);
        r.setCreatedBy(rs.getString("created_by"));
        r.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return r;
    }

    public List<Reservation> findAll() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Reservation> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reservations ORDER BY created_at DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        });
    }

    public Optional<Reservation> findById(String id) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reservations WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<Reservation> findByGuestName(String name) {
        if (name == null || name.isBlank()) return findAll();
        return DatabaseManager.getInstance().execute(conn -> {
            List<Reservation> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reservations WHERE LOWER(guest_name) LIKE LOWER(?) ORDER BY created_at DESC")) {
                ps.setString(1, "%" + name + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    public void save(Reservation r) {
        DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reservations (id, guest_name, guest_address, contact_number, email, nationality, room_id, check_in_date, check_out_date, nights, status, special_requests, sub_total, tax_amount, discount, total_amount, is_loyalty, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getGuestName());
                ps.setString(3, r.getGuestAddress());
                ps.setString(4, r.getContactNumber());
                ps.setString(5, r.getEmail());
                ps.setString(6, r.getNationality());
                ps.setString(7, r.getRoomId());
                ps.setObject(8, r.getCheckInDate());
                ps.setObject(9, r.getCheckOutDate());
                ps.setInt(10, r.getNights());
                ps.setString(11, r.getStatus());
                ps.setString(12, r.getSpecialRequests());
                ps.setBigDecimal(13, r.getSubTotal());
                ps.setBigDecimal(14, r.getTaxAmount());
                ps.setBigDecimal(15, r.getDiscount());
                ps.setBigDecimal(16, r.getTotalAmount());
                ps.setInt(17, r.isLoyalty() ? 1 : 0);
                ps.setString(18, r.getCreatedBy());
                ps.setObject(19, r.getCreatedAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public int updateStatus(String id, String status) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE reservations SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setString(2, id);
                return ps.executeUpdate();
            }
        });
    }

    public int updateBillFields(String id, BigDecimal subTotal, BigDecimal taxAmount, BigDecimal discount, BigDecimal totalAmount, boolean loyalty) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE reservations SET sub_total=?, tax_amount=?, discount=?, total_amount=?, is_loyalty=? WHERE id=?")) {
                ps.setBigDecimal(1, subTotal);
                ps.setBigDecimal(2, taxAmount);
                ps.setBigDecimal(3, discount);
                ps.setBigDecimal(4, totalAmount);
                ps.setInt(5, loyalty ? 1 : 0);
                ps.setString(6, id);
                return ps.executeUpdate();
            }
        });
    }
}
