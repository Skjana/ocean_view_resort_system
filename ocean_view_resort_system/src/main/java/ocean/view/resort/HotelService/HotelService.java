package ocean.view.resort.HotelService;

import ocean.view.resort.expoert.CsvExportAdapter;
import ocean.view.resort.expoert.ExportAdapter;
import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.manager.UserFactory;
import ocean.view.resort.model.*;
import ocean.view.resort.observer.EventBus;
import ocean.view.resort.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class HotelService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final ExtraChargesRepository extraChargesRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final HousekeepingLogRepository housekeepingLogRepository;
    private final EventBus eventBus;

    public HotelService(UserRepository userRepository, ReservationRepository reservationRepository,
                        RoomRepository roomRepository,
                        GuestRepository guestRepository, ExtraChargesRepository extraChargesRepository,
                        MaintenanceRepository maintenanceRepository, HousekeepingLogRepository housekeepingLogRepository,
                        EventBus eventBus) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.extraChargesRepository = extraChargesRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.housekeepingLogRepository = housekeepingLogRepository;
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

    public List<Room> getAvailableRooms(String roomType, LocalDate checkIn, LocalDate checkOut) {
        return roomRepository.findAvailableByTypeAndDates(roomType, checkIn, checkOut);
    }

    public Optional<Reservation> createReservation(CreateReservationRequest req, String userId) {
        List<Room> available = roomRepository.findAvailableByTypeAndDates(req.getRoomType(), req.getCheckInDate(), req.getCheckOutDate());
        if (available.isEmpty()) {
            eventBus.emit("notification", Map.of("type", "error", "msg", "No rooms available for selected dates."));
            return Optional.empty();
        }
        Room room = available.get(0);
        int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(req.getCheckInDate(), req.getCheckOutDate());
        BigDecimal rate = room.getNightlyRate();
        BigDecimal subTotal = rate.multiply(BigDecimal.valueOf(nights));
        BigDecimal tax = subTotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subTotal.add(tax).subtract(discount);

        String nextId = getNextReservationId();
        Reservation r = new Reservation();
        r.setId(nextId);
        r.setGuestName(req.getGuestName());
        r.setGuestAddress(req.getGuestAddress());
        r.setContactNumber(req.getContactNumber());
        r.setEmail(req.getEmail());
        r.setNationality(req.getNationality());
        r.setRoomId(room.getId());
        r.setCheckInDate(req.getCheckInDate());
        r.setCheckOutDate(req.getCheckOutDate());
        r.setNights(nights);
        r.setStatus("CONFIRMED");
        r.setSpecialRequests(req.getSpecialRequests() != null ? req.getSpecialRequests() : "");
        r.setSubTotal(subTotal);
        r.setTaxAmount(tax);
        r.setDiscount(discount);
        r.setTotalAmount(total);
        r.setLoyalty(false);
        r.setCreatedBy(userId);
        r.setCreatedAt(LocalDateTime.now());
        reservationRepository.save(r);
        eventBus.emit("reservation:created", r);
        eventBus.emit("notification", Map.of("type", "success", "msg", "Reservation " + r.getId() + " confirmed!"));
        return Optional.of(r);
    }

    public Optional<Reservation> cancelReservation(String id, String userId) {
        Optional<Reservation> res = reservationRepository.findById(id);
        if (res.isEmpty()) return Optional.empty();
        reservationRepository.updateStatus(id, "CANCELLED");
        eventBus.emit("notification", Map.of("type", "warning", "msg", "Reservation " + id + " has been cancelled."));
        return res;
    }

    public Optional<CheckOutResult> checkOut(String id) {
        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (resOpt.isEmpty()) return Optional.empty();
        Reservation res = resOpt.get();
        BillResult bill = calculateBill(id);
        if (bill == null) return Optional.empty();
        reservationRepository.updateBillFields(id, bill.getSubTotal(), bill.getTax(), bill.getDiscount(), bill.getTotal(), bill.isLoyalty());
        reservationRepository.updateStatus(id, "CHECKED_OUT");
        eventBus.emit("notification", Map.of("type", "success", "msg", "Guest checked out. Total: LKR " + bill.getTotal()));
        return Optional.of(new CheckOutResult(res, bill));
    }

    public BillResult calculateBill(String reservationId) {
        return DatabaseManager.getInstance().execute(conn -> {
            try (CallableStatement cs = conn.prepareCall("{CALL sp_calculate_bill(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
                cs.setString(1, reservationId);
                cs.registerOutParameter(2, Types.DECIMAL);
                cs.registerOutParameter(3, Types.DECIMAL);
                cs.registerOutParameter(4, Types.DECIMAL);
                cs.registerOutParameter(5, Types.DECIMAL);
                cs.registerOutParameter(6, Types.TINYINT);
                cs.registerOutParameter(7, Types.INTEGER);
                cs.registerOutParameter(8, Types.DECIMAL);
                cs.registerOutParameter(9, Types.VARCHAR);
                cs.registerOutParameter(10, Types.VARCHAR);
                cs.execute();
                BillResult b = new BillResult();
                b.setSubTotal(cs.getBigDecimal(2));
                b.setTax(cs.getBigDecimal(3));
                b.setDiscount(cs.getBigDecimal(4));
                b.setTotal(cs.getBigDecimal(5));
                b.setLoyalty(cs.getInt(6) == 1);
                b.setNights(cs.getInt(7));
                b.setNightlyRate(cs.getBigDecimal(8));
                b.setRoomType(cs.getString(9));
                b.setRoomNumber(cs.getString(10));
                return b;
            }
        });
    }

    /** Build HTML for bill print/download (includes extra charges if any). */
    public String getBillPrintHtml(String reservationId) {
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) return "<p>Reservation not found.</p>";
        Reservation res = resOpt.get();
        BillResult bill = calculateBill(reservationId);
        if (bill == null) return "<p>Could not calculate bill.</p>";
        List<ExtraCharge> extras = getExtraCharges(reservationId);
        BigDecimal extrasTotal = extras.stream().map(ExtraCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotal = bill.getTotal().add(extrasTotal);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Invoice ").append(reservationId).append("</title>");
        sb.append("<style>body{font-family:sans-serif;max-width:600px;margin:20px auto;padding:20px;} table{width:100%;border-collapse:collapse;} th,td{border:1px solid #ccc;padding:8px;text-align:left;} th{background:#f5f5f5;} .total{font-weight:bold;}</style></head><body>");
        sb.append("<h1>Ocean View Resort</h1><h2>Invoice - ").append(reservationId).append("</h2>");
        sb.append("<p><strong>Guest:</strong> ").append(escapeHtml(res.getGuestName())).append("</p>");
        sb.append("<p>").append(escapeHtml(res.getGuestAddress())).append(" | ").append(res.getContactNumber()).append(" | ").append(escapeHtml(res.getEmail())).append("</p>");
        sb.append("<p>Room ").append(bill.getRoomNumber()).append(" - ").append(bill.getRoomType()).append(" | ").append(res.getCheckInDate()).append(" to ").append(res.getCheckOutDate()).append(" (").append(bill.getNights()).append(" nights)</p>");
        sb.append("<table><tr><th>Description</th><th>Amount (LKR)</th></tr>");
        sb.append("<tr><td>Room (").append(bill.getNights()).append(" x ").append(bill.getNightlyRate()).append(")</td><td>").append(bill.getSubTotal()).append("</td></tr>");
        sb.append("<tr><td>VAT 10%</td><td>").append(bill.getTax()).append("</td></tr>");
        if (bill.getDiscount() != null && bill.getDiscount().signum() > 0) sb.append("<tr><td>Discount</td><td>-").append(bill.getDiscount()).append("</td></tr>");
        for (ExtraCharge e : extras) sb.append("<tr><td>").append(escapeHtml(e.getDescription())).append("</td><td>").append(e.getAmount()).append("</td></tr>");
        sb.append("<tr class='total'><td>TOTAL</td><td>").append(grandTotal).append("</td></tr></table>");
        sb.append("<p style='margin-top:24px'>Thank you for staying with us.</p></body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public String getNextReservationId() {
        return DatabaseManager.getInstance().execute(conn -> {
            try (CallableStatement cs = conn.prepareCall("{CALL sp_next_reservation_id(?)}")) {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                return cs.getString(1);
            }
        });
    }

    public List<Reservation> getAllReservations() { return reservationRepository.findAll(); }
    public Optional<Reservation> getReservationById(String id) { return reservationRepository.findById(id); }
    public List<Reservation> findReservationsByGuest(String name) { return reservationRepository.findByGuestName(name); }
    public List<Room> getAllRooms() { return roomRepository.findAll(); }
    public Optional<Room> getRoomById(String id) { return roomRepository.findById(id); }
    public List<Map<String, Object>> getAllUsers() { return userRepository.findAllUsers(); }

    /**
     * Create a new user (staff or administrator). Admin-only.
     * Validates username uniqueness and required fields.
     * @return the created user
     * @throws IllegalArgumentException if validation fails (duplicate username, missing fields)
     */
    public User createUser(String username, String password, String fullName, String email, String role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password is required");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required");
        String r = (role != null && "ADMINISTRATOR".equalsIgnoreCase(role.trim())) ? "ADMINISTRATOR" : "STAFF";
        if (userRepository.existsByUsername(username)) throw new IllegalArgumentException("Username already exists");
        String id = userRepository.getNextUserId();
        String hash = sha256Hex(password);
        userRepository.save(id, username, hash, r, fullName, email);
        eventBus.emit("notification", Map.of("type", "success", "msg", "User " + username + " created."));
        return userRepository.findById(id).map(UserFactory::create).orElseThrow(() -> new RuntimeException("User not found after create"));
    }

    /**
     * Update an existing user's profile. Admin-only.
     * Optionally change password (pass null or empty to keep current).
     * Cannot demote the last administrator.
     */
    public User updateUser(String id, String fullName, String email, String role, String newPassword) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("User ID is required");
        var row = userRepository.findById(id);
        if (row.isEmpty()) throw new IllegalArgumentException("User not found");
        String currentRole = (String) row.get().get("role");
        String r = (role != null && "ADMINISTRATOR".equalsIgnoreCase(role.trim())) ? "ADMINISTRATOR" : "STAFF";
        if ("ADMINISTRATOR".equals(currentRole) && "STAFF".equals(r)) {
            if (userRepository.countActiveAdministrators() <= 1)
                throw new IllegalArgumentException("Cannot demote the last administrator");
        }
        String hash = (newPassword != null && !newPassword.isBlank()) ? sha256Hex(newPassword) : null;
        userRepository.update(id, fullName, email, r, hash);
        eventBus.emit("notification", Map.of("type", "success", "msg", "User updated."));
        return userRepository.findById(id).map(UserFactory::create).orElseThrow(() -> new RuntimeException("User not found after update"));
    }

    /**
     * Deactivate a user (soft delete). They cannot log in and are hidden from the list. Admin-only.
     * Cannot deactivate self or the last administrator.
     */
    public void deactivateUser(String id, String currentUserUsername) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("User ID is required");
        var row = userRepository.findById(id);
        if (row.isEmpty()) throw new IllegalArgumentException("User not found");
        String username = (String) row.get().get("username");
        if (username != null && username.equals(currentUserUsername))
            throw new IllegalArgumentException("You cannot deactivate your own account");
        if ("ADMINISTRATOR".equals(row.get().get("role")) && userRepository.countActiveAdministrators() <= 1)
            throw new IllegalArgumentException("Cannot deactivate the last administrator");
        userRepository.deactivate(id);
        eventBus.emit("notification", Map.of("type", "success", "msg", "User deactivated."));
    }

    public Optional<User> getUserByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return userRepository.findByUsername(username.trim()).map(UserFactory::create);
    }

    /** Get user by id (for admin edit form). Returns empty if not found. */
    public Optional<User> getUserById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return userRepository.findById(id).map(UserFactory::create);
    }

    public Optional<User> getDefaultUser() {
        List<Map<String, Object>> all = userRepository.findAllUsers();
        if (all.isEmpty()) return Optional.empty();
        return Optional.of(UserFactory.create(all.get(0)));
    }

    public Map<String, OccupancyRow> getOccupancyReport() {
        return DatabaseManager.getInstance().execute(conn -> {
            Map<String, OccupancyRow> out = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM vw_occupancy_report");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OccupancyRow o = new OccupancyRow();
                    o.setRoomType(rs.getString("room_type"));
                    o.setTotalRooms(rs.getInt("total_rooms"));
                    o.setOccupied(rs.getInt("occupied"));
                    o.setNightlyRate(rs.getBigDecimal("nightly_rate") != null ? rs.getBigDecimal("nightly_rate") : BigDecimal.ZERO);
                    out.put(o.getRoomType(), o);
                }
            }
            return out;
        });
    }

    public List<RevenueRow> getRevenueReport() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<RevenueRow> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM vw_revenue_report");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RevenueRow row = new RevenueRow();
                    row.setMonth(rs.getString("month"));
                    row.setBookings(rs.getInt("bookings"));
                    row.setRevenue(rs.getBigDecimal("revenue"));
                    rows.add(row);
                }
            }
            return rows;
        });
    }

    // ---------- Guests (profile, history, loyalty) ----------
    public List<Guest> getAllGuests() { return guestRepository.findAll(); }
    public Optional<Guest> getGuestById(int id) { return guestRepository.findById(id); }
    public List<Guest> searchGuests(String query) { return guestRepository.searchByNameOrEmail(query); }
    public Guest createGuest(Guest g) { return guestRepository.save(g); }
    public void updateGuest(Guest g) { guestRepository.update(g); }

    // ---------- Extra charges (mini-bar, room service) ----------
    public List<ExtraCharge> getExtraCharges(String reservationId) { return extraChargesRepository.findByReservationId(reservationId); }
    public void addExtraCharge(String reservationId, String description, BigDecimal amount, String createdBy) {
        extraChargesRepository.save(reservationId, description, amount, createdBy);
    }
    public void removeExtraCharge(int id) { extraChargesRepository.delete(id); }

    // ---------- Maintenance issues (per room) ----------
    public List<MaintenanceIssue> getAllMaintenanceIssues() { return maintenanceRepository.findAll(); }
    public List<MaintenanceIssue> getMaintenanceByRoom(String roomId) { return maintenanceRepository.findByRoomId(roomId); }
    public MaintenanceIssue createMaintenanceIssue(MaintenanceIssue m) { return maintenanceRepository.save(m); }
    public void updateMaintenanceStatus(int id, String status) { maintenanceRepository.updateStatus(id, status); }
    public void assignMaintenance(int id, String userId) { maintenanceRepository.updateAssignedTo(id, userId); }

    // ---------- Room status / Housekeeping ----------
    public void setRoomStatus(String roomId, String status, String staffId, String notes) {
        roomRepository.updateRoomStatus(roomId, status);
        housekeepingLogRepository.log(roomId, status, staffId, notes);
    }

    // ---------- Date-range report (Builder: ReportCriteria) ----------
    public List<Map<String, Object>> getRevenueByDateRange(LocalDate from, LocalDate to) {
        return DatabaseManager.getInstance().execute(conn -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            String sql = "SELECT DATE_FORMAT(check_in_date,'%Y-%m-%d') AS date, COUNT(*) AS bookings, COALESCE(SUM(total_amount),0) AS revenue FROM reservations WHERE status <> 'CANCELLED'";
            if (from != null) sql += " AND check_in_date >= ?";
            if (to != null) sql += " AND check_in_date <= ?";
            sql += " GROUP BY check_in_date ORDER BY check_in_date";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                if (from != null) ps.setObject(i++, from);
                if (to != null) ps.setObject(i++, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("date", rs.getString("date"));
                        m.put("bookings", rs.getInt("bookings"));
                        m.put("revenue", rs.getBigDecimal("revenue"));
                        rows.add(m);
                    }
                }
            }
            return rows;
        });
    }

    // ---------- Export (Adapter: CSV) ----------
    public byte[] exportReservationsCsv(LocalDate from, LocalDate to) throws Exception {
        List<Reservation> list = reservationRepository.findAll();
        if (from != null || to != null) {
            list = list.stream().filter(r -> {
                if (from != null && r.getCheckInDate().isBefore(from)) return false;
                if (to != null && r.getCheckInDate().isAfter(to)) return false;
                return true;
            }).collect(Collectors.toList());
        }
        List<String> headers = List.of("id", "guestName", "roomId", "checkInDate", "checkOutDate", "nights", "status", "totalAmount");
        List<Map<String, Object>> rows = list.stream().map(r -> Map.<String, Object>of(
                "id", r.getId(), "guestName", r.getGuestName(), "roomId", r.getRoomId(),
                "checkInDate", r.getCheckInDate().toString(), "checkOutDate", r.getCheckOutDate().toString(),
                "nights", r.getNights(), "status", r.getStatus(), "totalAmount", r.getTotalAmount())).collect(Collectors.toList());
        ExportAdapter adapter = new CsvExportAdapter();
        return adapter.export(headers, rows);
    }

    public byte[] exportRevenueCsv(LocalDate from, LocalDate to) throws Exception {
        List<Map<String, Object>> rows = getRevenueByDateRange(from, to);
        ExportAdapter adapter = new CsvExportAdapter();
        return adapter.export(List.of("date", "bookings", "revenue"), rows);
    }

    public List<AuditEntry> getAuditLog() {
        return DatabaseManager.getInstance().execute(conn -> {
            List<AuditEntry> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, action, table_name, record_id, user_id, detail, created_at FROM audit_log ORDER BY created_at DESC LIMIT 500");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry e = new AuditEntry();
                    e.setId(rs.getInt("id"));
                    e.setAction(rs.getString("action"));
                    e.setTableName(rs.getString("table_name"));
                    e.setRecordId(rs.getString("record_id"));
                    e.setUserId(rs.getString("user_id"));
                    e.setDetail(rs.getString("detail"));
                    e.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                    list.add(e);
                }
            }
            return list;
        });
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

    // DTOs for API
    public static class CreateReservationRequest {
        private String guestName, guestAddress, contactNumber, email, nationality, roomType, specialRequests;
        private LocalDate checkInDate, checkOutDate;
        public String getGuestName() { return guestName; } public void setGuestName(String guestName) { this.guestName = guestName; }
        public String getGuestAddress() { return guestAddress; } public void setGuestAddress(String guestAddress) { this.guestAddress = guestAddress; }
        public String getContactNumber() { return contactNumber; } public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
        public String getEmail() { return email; } public void setEmail(String email) { this.email = email; }
        public String getNationality() { return nationality; } public void setNationality(String nationality) { this.nationality = nationality; }
        public String getRoomType() { return roomType; } public void setRoomType(String roomType) { this.roomType = roomType; }
        public String getSpecialRequests() { return specialRequests; } public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }
        public LocalDate getCheckInDate() { return checkInDate; } public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
        public LocalDate getCheckOutDate() { return checkOutDate; } public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    }

    public static class CheckOutResult {
        private Reservation reservation;
        private BillResult bill;
        public CheckOutResult(Reservation reservation, BillResult bill) { this.reservation = reservation; this.bill = bill; }
        public Reservation getReservation() { return reservation; }
        public BillResult getBill() { return bill; }
    }

    public static class OccupancyRow {
        private String roomType; private int totalRooms, occupied; private BigDecimal nightlyRate;
        public String getRoomType() { return roomType; } public void setRoomType(String roomType) { this.roomType = roomType; }
        public int getTotalRooms() { return totalRooms; } public void setTotalRooms(int totalRooms) { this.totalRooms = totalRooms; }
        public int getOccupied() { return occupied; } public void setOccupied(int occupied) { this.occupied = occupied; }
        public BigDecimal getNightlyRate() { return nightlyRate; } public void setNightlyRate(BigDecimal nightlyRate) { this.nightlyRate = nightlyRate; }
    }

    public static class RevenueRow {
        private String month; private int bookings; private BigDecimal revenue;
        public String getMonth() { return month; } public void setMonth(String month) { this.month = month; }
        public int getBookings() { return bookings; } public void setBookings(int bookings) { this.bookings = bookings; }
        public BigDecimal getRevenue() { return revenue; } public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    }
}
