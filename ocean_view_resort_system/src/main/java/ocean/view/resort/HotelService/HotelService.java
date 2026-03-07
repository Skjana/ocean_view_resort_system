package ocean.view.resort.HotelService;

import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.manager.UserFactory;
import ocean.view.resort.model.BillResult;
import ocean.view.resort.model.Reservation;
import ocean.view.resort.model.Room;
import ocean.view.resort.model.User;
import ocean.view.resort.observer.EventBus;
import ocean.view.resort.repository.ReservationRepository;
import ocean.view.resort.repository.RoomRepository;
import ocean.view.resort.repository.UserRepository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HotelService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final EventBus eventBus;

    public HotelService(UserRepository userRepository, ReservationRepository reservationRepository,
                        RoomRepository roomRepository,EventBus eventBus) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
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

    public List<Reservation> getAllReservations() { return reservationRepository.findAll(); }
    public Optional<Reservation> getReservationById(String id) { return reservationRepository.findById(id); }
    public List<Reservation> findReservationsByGuest(String name) { return reservationRepository.findByGuestName(name); }
    public List<Room> getAllRooms() { return roomRepository.findAll(); }
    public Optional<Room> getRoomById(String id) { return roomRepository.findById(id); }
    public List<Map<String, Object>> getAllUsers() { return userRepository.findAllUsers(); }

    //    Reservation
    public Optional<Reservation> cancelReservation(String id, String userId) {
        Optional<Reservation> res = reservationRepository.findById(id);
        if (res.isEmpty()) return Optional.empty();
        reservationRepository.updateStatus(id, "CANCELLED");
        eventBus.emit("notification", Map.of("type", "warning", "msg", "Reservation " + id + " has been cancelled."));
        return res;
    }

    // users
    public Optional<User> getUserById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return userRepository.findById(id).map(UserFactory::create);
    }

    // Rooms
    public List<Room> getAvailableRooms(String roomType, LocalDate checkIn, LocalDate checkOut) {
        return roomRepository.findAvailableByTypeAndDates(roomType, checkIn, checkOut);
    }

    public static class CheckOutResult {
        private Reservation reservation;
        private BillResult bill;
        public CheckOutResult(Reservation reservation, BillResult bill) { this.reservation = reservation; this.bill = bill; }
        public Reservation getReservation() { return reservation; }
        public BillResult getBill() { return bill; }
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

    //  bill calculation
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
//        List<ExtraCharge> extras = getExtraCharges(reservationId);
//        BigDecimal extrasTotal = extras.stream().map(ExtraCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
//        BigDecimal grandTotal = bill.getTotal().add(extrasTotal);
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
//        for (ExtraCharge e : extras) sb.append("<tr><td>").append(escapeHtml(e.getDescription())).append("</td><td>").append(e.getAmount()).append("</td></tr>");
//        sb.append("<tr class='total'><td>TOTAL</td><td>").append(grandTotal).append("</td></tr></table>");
        sb.append("<p style='margin-top:24px'>Thank you for staying with us.</p></body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
