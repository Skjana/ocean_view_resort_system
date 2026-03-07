package ocean.view.resort;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ocean.view.resort.HotelService.HotelService;
import ocean.view.resort.manager.DatabaseManager;
import ocean.view.resort.model.BillResult;
import ocean.view.resort.model.Reservation;
import ocean.view.resort.model.Room;
import ocean.view.resort.model.User;
import ocean.view.resort.observer.AuditLogListener;
import ocean.view.resort.observer.EventBus;
import ocean.view.resort.repository.ExtraChargesRepository;
import ocean.view.resort.repository.ReservationRepository;
import ocean.view.resort.repository.RoomRepository;
import ocean.view.resort.repository.UserRepository;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;


public class Main {

    private static final Gson GSON = new Gson();
    private static HotelService hotelService;

    public static void main(String[] args) throws IOException {
        // Load config
        Properties props = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        }
        String jdbcUrl = props.getProperty("spring.datasource.url",
                "jdbc:mysql://localhost:3306/ocean_view_resort?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        String jdbcUser = props.getProperty("spring.datasource.username", "root");
        String jdbcPass = props.getProperty("spring.datasource.password", "root");
        int port = Integer.parseInt(props.getProperty("server.port", "8080"));

        // DataSource (HikariCP)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(jdbcUser);
        config.setPassword(jdbcPass);
        DataSource dataSource = new HikariDataSource(config);

        // Singleton + Repositories + Observer + Facade (pure Java wiring)
        DatabaseManager.init(dataSource);
        UserRepository userRepository = new UserRepository();
        ReservationRepository reservationRepository = new ReservationRepository();
        RoomRepository roomRepository = new RoomRepository();
        ocean.view.resort.repository.GuestRepository guestRepository = new ocean.view.resort.repository.GuestRepository();
        ocean.view.resort.repository.ExtraChargesRepository extraChargesRepository = new ocean.view.resort.repository.ExtraChargesRepository();
        ocean.view.resort.repository.MaintenanceRepository maintenanceRepository = new ocean.view.resort.repository.MaintenanceRepository();
        ocean.view.resort.repository.HousekeepingLogRepository housekeepingLogRepository = new ocean.view.resort.repository.HousekeepingLogRepository();
        EventBus eventBus = new EventBus();
        AuditLogListener auditLogListener = new AuditLogListener(eventBus);
        auditLogListener.init();
        hotelService = new HotelService(userRepository, reservationRepository, roomRepository,
                guestRepository, extraChargesRepository, maintenanceRepository, housekeepingLogRepository, eventBus);

        // HTTP server (JDK built-in): /api = REST, / = static UI (HTML/JS/CSS)
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", Main::handleApi);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("Ocean View Resort running at http://localhost:" + port + " (UI + API at /api, Java)");
    }

    private static void handleStatic(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path == null || path.isEmpty()) path = "/";
        if ("/favicon.ico".equals(path)) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        // Serve index.html for / or /index.html; otherwise the single path segment (e.g. styles.css, api.js, app.js)
        String resource = path.equals("/") || path.equalsIgnoreCase("/index.html") ? "index.html" : path.startsWith("/") ? path.substring(1) : path;
        if (resource.contains("..") || resource.contains("\\") || resource.contains("/")) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        if (resource.isEmpty()) resource = "index.html";
        String contentType = "application/octet-stream";
        if (resource.endsWith(".html")) contentType = "text/html; charset=UTF-8";
        else if (resource.endsWith(".css")) contentType = "text/css; charset=UTF-8";
        else if (resource.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
        try (InputStream in = Main.class.getResourceAsStream("/static/" + resource)) {
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    private static void handleApi(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String usernameHeader = ex.getRequestHeaders().getFirst("X-Username");
        String query = ex.getRequestURI().getQuery();
        User user = resolveUser(usernameHeader);

        try {
            Object body = method.equals("POST") || method.equals("PUT") ? readBody(ex) : null;
            Object result = route(path, method, query, body, user);
            if (result instanceof ApiError) {
                ApiError err = (ApiError) result;
                sendJson(ex, err.status, err.body);
            } else {
                sendJson(ex, 200, result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    private static Object route(String path, String method, String query, Object body, User user) {
        // Auth (no token: login returns user only; user resolved from X-Username or default)
        if (path.equals("/api/auth/login") && "POST".equals(method)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Username and password required"));
            String username = m.get("username") != null ? m.get("username").toString().trim() : null;
            String password = m.get("password") != null ? m.get("password").toString() : null;
            if (username == null || username.isEmpty() || password == null) return new ApiError(400, Map.of("error", "Username and password required"));
            var u = hotelService.authenticate(username, password);
            if (u.isEmpty()) return new ApiError(401, Map.of("error", "Invalid username or password"));
            return Map.of("user", mapUser(u.get()));
        }
        if (path.equals("/api/auth/logout") && "POST".equals(method)) {
            return Map.of("ok", true);
        }
        if (path.equals("/api/auth/me") && "GET".equals(method)) {
            if (user == null) return new ApiError(401, Map.of("error", "Not authenticated"));
            return mapUser(user);
        }

        if (user == null) return new ApiError(401, Map.of("error", "Not authenticated"));

        // Reservations
        if (path.equals("/api/reservations") && "GET".equals(method)) {
            List<Reservation> list = hotelService.getAllReservations();
            String status = getQueryParam(query, "status");
            String search = getQueryParam(query, "search");
            if (search != null && !search.isBlank()) list = hotelService.findReservationsByGuest(search);
            if (status != null && !status.isBlank() && !"ALL".equals(status))
                list = list.stream().filter(r -> status.equals(r.getStatus())).collect(Collectors.toList());
            return list.stream().map(Main::mapReservation).collect(Collectors.toList());
        }
        if (path.startsWith("/api/reservations/") && path.length() > 18) {
            String id = path.substring(18).split("/")[0];
            if ("GET".equals(method)) {
                var opt = hotelService.getReservationById(id).map(Main::mapReservationWithRoom);
                if (opt.isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
                return opt.get();
            }
            if (path.endsWith("/cancel") && "POST".equals(method)) {
                if (hotelService.cancelReservation(id, user.getUsername()).isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
                return Map.of("ok", true);
            }
            if (path.endsWith("/checkout") && "POST".equals(method)) {
                var r = hotelService.checkOut(id);
                if (r.isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
                return Map.of("reservation", mapReservation(r.get().getReservation()), "bill", mapBill(r.get().getBill()));
            }
        }
        if (path.equals("/api/reservations") && "POST".equals(method)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Body required"));
            HotelService.CreateReservationRequest req = new HotelService.CreateReservationRequest();
            req.setGuestName((String) m.get("guestName"));
            req.setGuestAddress((String) m.get("guestAddress"));
            req.setContactNumber((String) m.get("contactNumber"));
            req.setEmail((String) m.get("email"));
            req.setNationality((String) m.get("nationality"));
            req.setRoomType((String) m.get("roomType"));
            req.setSpecialRequests((String) m.get("specialRequests"));
            if (m.get("checkInDate") != null) req.setCheckInDate(java.time.LocalDate.parse((String) m.get("checkInDate")));
            if (m.get("checkOutDate") != null) req.setCheckOutDate(java.time.LocalDate.parse((String) m.get("checkOutDate")));
            var created = hotelService.createReservation(req, user.getUsername());
            if (created.isEmpty()) return new ApiError(400, Map.of("error", "No rooms available for selected dates"));
            return mapReservationWithRoom(created.get());
        }

        // Rooms
        if (path.equals("/api/rooms") && "GET".equals(method)) {
            return hotelService.getAllRooms().stream().map(Main::mapRoom).collect(Collectors.toList());
        }
        if (path.equals("/api/rooms/available") && "GET".equals(method)) {
            String roomType = getQueryParam(query, "roomType");
            String checkIn = getQueryParam(query, "checkIn");
            String checkOut = getQueryParam(query, "checkOut");
            if (roomType == null || checkIn == null || checkOut == null)
                return new ApiError(400, Map.of("error", "roomType, checkIn, checkOut required"));
            return hotelService.getAvailableRooms(roomType, java.time.LocalDate.parse(checkIn), java.time.LocalDate.parse(checkOut))
                    .stream().map(Main::mapRoom).collect(Collectors.toList());
        }
        if (path.startsWith("/api/rooms/") && path.endsWith("/status") && "PUT".equals(method)) {
            String roomId = path.substring(11, path.length() - 7);
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Body required"));
            String status = m.get("status") != null ? m.get("status").toString() : null;
            if (status == null) return new ApiError(400, Map.of("error", "status required"));
            String notes = m.get("notes") != null ? m.get("notes").toString() : null;
            hotelService.setRoomStatus(roomId, status, user.getUsername(), notes);
            return Map.of("ok", true);
        }

        // Billing (check /print and /extra-charges before generic GET so resId is not "RES-0006/print")
        if (path.startsWith("/api/billing/bill/") && path.endsWith("/print") && path.length() > 25 && "GET".equals(method)) {
            String resId = path.substring(18, path.length() - 6);
            String html = hotelService.getBillPrintHtml(resId);
            return Map.of("html", html);
        }
        if (path.startsWith("/api/billing/bill/") && path.endsWith("/extra-charges") && path.length() > 32) {
            String resId = path.substring(18, path.length() - 14);
            if ("GET".equals(method)) {
                return hotelService.getExtraCharges(resId).stream().map(Main::mapExtraCharge).collect(Collectors.toList());
            }
            if ("POST".equals(method)) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
                if (m == null) return new ApiError(400, Map.of("error", "Body required"));
                String desc = m.get("description") != null ? m.get("description").toString() : null;
                Object amt = m.get("amount");
                if (desc == null || amt == null) return new ApiError(400, Map.of("error", "description and amount required"));
                hotelService.addExtraCharge(resId, desc, new java.math.BigDecimal(amt.toString()), user.getUsername());
                return Map.of("ok", true);
            }
        }
        if (path.startsWith("/api/billing/bill/") && path.length() > 18 && "GET".equals(method)) {
            String resId = path.substring(18);
            var res = hotelService.getReservationById(resId);
            if (res.isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
            if ("CANCELLED".equals(res.get().getStatus())) return new ApiError(400, Map.of("error", "Cannot generate bill for a cancelled reservation"));
            var bill = hotelService.calculateBill(resId);
            if (bill == null) return new ApiError(500, Map.of("error", "Failed to calculate bill"));
            var room = hotelService.getRoomById(res.get().getRoomId());
            return Map.of(
                    "reservation", mapReservationMin(res.get()),
                    "bill", mapBill(bill),
                    "room", room.isPresent() ? Map.of("roomNumber", room.get().getRoomNumber(), "roomType", room.get().getRoomType()) : Map.of()
            );
        }
        if (path.startsWith("/api/extra-charges/") && path.length() > 19 && "DELETE".equals(method)) {
            String idStr = path.substring(18).split("/")[0];
            try {
                hotelService.removeExtraCharge(Integer.parseInt(idStr));
                return Map.of("ok", true);
            } catch (NumberFormatException e) { return new ApiError(400, Map.of("error", "Invalid id")); }
        }

        // Reports
        if (path.equals("/api/reports/occupancy") && "GET".equals(method)) {
            return hotelService.getOccupancyReport();
        }
        if (path.equals("/api/reports/revenue") && "GET".equals(method)) {
            return hotelService.getRevenueReport().stream()
                    .map(r -> Map.<String, Object>of("month", r.getMonth(), "bookings", r.getBookings(), "revenue", r.getRevenue()))
                    .collect(Collectors.toList());
        }
        if (path.equals("/api/reports/audit") && "GET".equals(method)) {
            if (!"ADMINISTRATOR".equals(user.getRole())) return new ApiError(403, Map.of("error", "Admin only"));
            return hotelService.getAuditLog().stream()
                    .map(e -> Map.<String, Object>of("id", e.getId(), "action", e.getAction(), "tableName", e.getTableName(), "recordId", nullToEmpty(e.getRecordId()), "userId", nullToEmpty(e.getUserId()), "detail", nullToEmpty(e.getDetail()), "createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : ""))
                    .collect(Collectors.toList());
        }
        if (path.equals("/api/reports/revenue-by-range") && "GET".equals(method)) {
            String from = getQueryParam(query, "from");
            String to = getQueryParam(query, "to");
            java.time.LocalDate fromDate = from != null && !from.isBlank() ? java.time.LocalDate.parse(from) : null;
            java.time.LocalDate toDate = to != null && !to.isBlank() ? java.time.LocalDate.parse(to) : null;
            return hotelService.getRevenueByDateRange(fromDate, toDate);
        }
        if (path.startsWith("/api/export/") && "GET".equals(method)) {
            String type = getQueryParam(query, "type");
            String from = getQueryParam(query, "from");
            String to = getQueryParam(query, "to");
            java.time.LocalDate fromDate = from != null && !from.isBlank() ? java.time.LocalDate.parse(from) : null;
            java.time.LocalDate toDate = to != null && !to.isBlank() ? java.time.LocalDate.parse(to) : null;
            try {
                byte[] bytes;
                String filename;
                if ("revenue".equals(type)) {
                    bytes = hotelService.exportRevenueCsv(fromDate, toDate);
                    filename = "revenue-by-date.csv";
                } else {
                    bytes = hotelService.exportReservationsCsv(fromDate, toDate);
                    filename = "reservations.csv";
                }
                return Map.of("content", java.util.Base64.getEncoder().encodeToString(bytes), "filename", filename);
            } catch (Exception e) {
                return new ApiError(500, Map.of("error", e.getMessage()));
            }
        }

        // Guests
        if (path.equals("/api/guests") && "GET".equals(method)) {
            String q = getQueryParam(query, "q");
            List<ocean.view.resort.model.Guest> list = q != null && !q.isBlank() ? hotelService.searchGuests(q) : hotelService.getAllGuests();
            return list.stream().map(Main::mapGuest).collect(Collectors.toList());
        }
        if (path.equals("/api/guests") && "POST".equals(method)) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Body required"));
            ocean.view.resort.model.Guest g = new ocean.view.resort.model.Guest();
            g.setFullName(m.get("fullName") != null ? m.get("fullName").toString() : null);
            g.setEmail(m.get("email") != null ? m.get("email").toString() : null);
            g.setPhone(m.get("phone") != null ? m.get("phone").toString() : null);
            g.setAddress(m.get("address") != null ? m.get("address").toString() : null);
            g.setNationality(m.get("nationality") != null ? m.get("nationality").toString() : null);
            g.setNotes(m.get("notes") != null ? m.get("notes").toString() : null);
            ocean.view.resort.model.Guest created = hotelService.createGuest(g);
            return mapGuest(created);
        }
        if (path.startsWith("/api/guests/") && path.length() > 12) {
            String idStr = path.substring(12).split("/")[0];
            try {
                int id = Integer.parseInt(idStr);
                if ("GET".equals(method)) {
                    var gOpt = hotelService.getGuestById(id).map(Main::mapGuest);
                    if (gOpt.isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
                    return gOpt.get();
                }
                if ("PUT".equals(method)) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
                    if (m == null) return new ApiError(400, Map.of("error", "Body required"));
                    var gOpt = hotelService.getGuestById(id);
                    if (gOpt.isEmpty()) return new ApiError(404, Map.of("error", "Not found"));
                    ocean.view.resort.model.Guest g = gOpt.get();
                    g.setFullName(m.get("fullName") != null ? m.get("fullName").toString() : g.getFullName());
                    g.setEmail(m.get("email") != null ? m.get("email").toString() : g.getEmail());
                    g.setPhone(m.get("phone") != null ? m.get("phone").toString() : g.getPhone());
                    g.setAddress(m.get("address") != null ? m.get("address").toString() : g.getAddress());
                    g.setNationality(m.get("nationality") != null ? m.get("nationality").toString() : g.getNationality());
                    g.setNotes(m.get("notes") != null ? m.get("notes").toString() : g.getNotes());
                    hotelService.updateGuest(g);
                    return mapGuest(g);
                }
                return new ApiError(404, Map.of("error", "Not found"));
            } catch (NumberFormatException e) { return new ApiError(400, Map.of("error", "Invalid id")); }
        }

        // Maintenance
        if (path.equals("/api/maintenance") && "GET".equals(method)) {
            String roomId = getQueryParam(query, "roomId");
            List<ocean.view.resort.model.MaintenanceIssue> list = roomId != null ? hotelService.getMaintenanceByRoom(roomId) : hotelService.getAllMaintenanceIssues();
            return list.stream().map(Main::mapMaintenance).collect(Collectors.toList());
        }
        if (path.equals("/api/maintenance") && "POST".equals(method)) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Body required"));
            ocean.view.resort.model.MaintenanceIssue mi = new ocean.view.resort.model.MaintenanceIssue();
            mi.setRoomId(m.get("roomId") != null ? m.get("roomId").toString() : null);
            mi.setTitle(m.get("title") != null ? m.get("title").toString() : null);
            mi.setDescription(m.get("description") != null ? m.get("description").toString() : null);
            mi.setCategory(m.get("category") != null ? m.get("category").toString() : "OTHER");
            mi.setStatus(m.get("status") != null ? m.get("status").toString() : "OPEN");
            mi.setAssignedTo(m.get("assignedTo") != null ? m.get("assignedTo").toString() : null);
            ocean.view.resort.model.MaintenanceIssue created = hotelService.createMaintenanceIssue(mi);
            return mapMaintenance(created);
        }
        if (path.startsWith("/api/maintenance/") && path.length() > 17) {
            String idStr = path.substring(17).split("/")[0];
            try {
                int id = Integer.parseInt(idStr);
                if ("PATCH".equals(method)) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) body;
                    if (m != null && m.containsKey("status")) hotelService.updateMaintenanceStatus(id, m.get("status").toString());
                    if (m != null && m.containsKey("assignedTo")) hotelService.assignMaintenance(id, m.get("assignedTo").toString());
                    return hotelService.getAllMaintenanceIssues().stream().filter(i -> i.getId() != null && i.getId() == id).findFirst().map(Main::mapMaintenance).orElse(new HashMap<>());
                }
                return new ApiError(404, Map.of("error", "Not found"));
            } catch (NumberFormatException e) { return new ApiError(400, Map.of("error", "Invalid id")); }
        }

        // Users (admin-only: list, add, edit, delete staff)
        if (path.equals("/api/users") && "GET".equals(method)) {
            if (!user.isCanManageUsers()) return new ApiError(403, Map.of("error", "Admin only"));
            return hotelService.getAllUsers();
        }
        if (path.startsWith("/api/users/") && path.length() > 11 && "GET".equals(method)) {
            String userId = path.substring(11).split("/")[0].trim();
            if (userId.isEmpty()) return new ApiError(400, Map.of("error", "User ID required"));
            if (!user.isCanManageUsers()) return new ApiError(403, Map.of("error", "Admin only"));
            var uOpt = hotelService.getUserById(userId);
            if (uOpt.isEmpty()) return new ApiError(404, Map.of("error", "User not found"));
            return mapUserForAdmin(uOpt.get());
        }
        if (path.equals("/api/users") && "POST".equals(method)) {
            if (!user.isCanManageUsers()) return new ApiError(403, Map.of("error", "Admin only"));
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) body;
            if (m == null) return new ApiError(400, Map.of("error", "Body required"));
            String username = m.get("username") != null ? m.get("username").toString().trim() : null;
            String password = m.get("password") != null ? m.get("password").toString() : null;
            String fullName = m.get("fullName") != null ? m.get("fullName").toString().trim() : (m.get("full_name") != null ? m.get("full_name").toString().trim() : null);
            String email = m.get("email") != null ? m.get("email").toString().trim() : null;
            String role = m.get("role") != null ? m.get("role").toString().trim().toUpperCase() : "STAFF";
            if (!"ADMINISTRATOR".equals(role)) role = "STAFF";
            try {
                User created = hotelService.createUser(username, password, fullName, email, role);
                return Map.of("user", mapUser(created));
            } catch (IllegalArgumentException e) {
                return new ApiError(400, Map.of("error", e.getMessage()));
            }
        }
        if (path.startsWith("/api/users/") && path.length() > 11) {
            String userId = path.substring(11).split("/")[0].trim();
            if (userId.isEmpty()) return new ApiError(400, Map.of("error", "User ID required"));
            if ("PUT".equals(method)) {
                if (!user.isCanManageUsers()) return new ApiError(403, Map.of("error", "Admin only"));
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) body;
                if (m == null) return new ApiError(400, Map.of("error", "Body required"));
                String fullName = m.get("fullName") != null ? m.get("fullName").toString().trim() : (m.get("full_name") != null ? m.get("full_name").toString().trim() : null);
                String email = m.get("email") != null ? m.get("email").toString().trim() : null;
                String role = m.get("role") != null ? m.get("role").toString().trim().toUpperCase() : "STAFF";
                String newPassword = m.get("password") != null ? m.get("password").toString() : null;
                if (newPassword != null && newPassword.isEmpty()) newPassword = null;
                if (!"ADMINISTRATOR".equals(role)) role = "STAFF";
                try {
                    User updated = hotelService.updateUser(userId, fullName, email, role, newPassword);
                    return Map.of("user", mapUser(updated));
                } catch (IllegalArgumentException e) {
                    return new ApiError(400, Map.of("error", e.getMessage()));
                }
            }
            if ("DELETE".equals(method)) {
                if (!user.isCanManageUsers()) return new ApiError(403, Map.of("error", "Admin only"));
                try {
                    hotelService.deactivateUser(userId, user.getUsername());
                    return Map.of("ok", true);
                } catch (IllegalArgumentException e) {
                    return new ApiError(400, Map.of("error", e.getMessage()));
                }
            }
        }

        return new ApiError(404, Map.of("error", "Not found"));
    }

    private static String getQueryParam(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(pair.substring(0, eq)))
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static void addCors(com.sun.net.httpserver.HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost:3000") || origin.startsWith("http://localhost:3001") || origin.startsWith("http://localhost:8080") || origin.startsWith("http://127.0.0.1:3000") || origin.startsWith("http://127.0.0.1:3001") || origin.startsWith("http://127.0.0.1:8080")))
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "X-Username, Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
    }

    private static Object readBody(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        String s = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        if (s.isEmpty()) return null;
        return GSON.fromJson(s, Map.class);
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static User resolveUser(String usernameHeader) {
        if (usernameHeader != null && !usernameHeader.isBlank()) {
            return hotelService.getUserByUsername(usernameHeader.trim()).orElse(null);
        }
        return null;
    }

    private static Map<String, Object> mapUser(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail() != null ? u.getEmail() : "");
        return m;
    }

    /** Same as mapUser but includes full_name for admin user list/edit API. */
    private static Map<String, Object> mapUserForAdmin(User u) {
        Map<String, Object> m = mapUser(u);
        m.put("full_name", u.getFullName());
        return m;
    }

    private static Map<String, Object> mapReservation(Reservation r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("guestName", r.getGuestName());
        m.put("guestAddress", nullToEmpty(r.getGuestAddress()));
        m.put("contactNumber", r.getContactNumber());
        m.put("email", nullToEmpty(r.getEmail()));
        m.put("nationality", nullToEmpty(r.getNationality()));
        m.put("roomId", r.getRoomId());
        m.put("checkInDate", r.getCheckInDate().toString());
        m.put("checkOutDate", r.getCheckOutDate().toString());
        m.put("nights", r.getNights());
        m.put("status", r.getStatus());
        m.put("specialRequests", nullToEmpty(r.getSpecialRequests()));
        m.put("subTotal", r.getSubTotal());
        m.put("taxAmount", r.getTaxAmount());
        m.put("discount", r.getDiscount());
        m.put("total", r.getTotalAmount());
        m.put("isLoyalty", r.isLoyalty());
        m.put("createdBy", nullToEmpty(r.getCreatedBy()));
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return m;
    }

    private static Map<String, Object> mapReservationWithRoom(Reservation r) {
        Map<String, Object> m = mapReservation(r);
        hotelService.getRoomById(r.getRoomId()).ifPresent(room -> m.put("roomNumber", room.getRoomNumber()));
        return m;
    }

    private static Map<String, Object> mapReservationMin(Reservation r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("guestName", r.getGuestName());
        m.put("guestAddress", nullToEmpty(r.getGuestAddress()));
        m.put("contactNumber", r.getContactNumber());
        m.put("email", nullToEmpty(r.getEmail()));
        m.put("checkInDate", r.getCheckInDate().toString());
        m.put("checkOutDate", r.getCheckOutDate().toString());
        m.put("nights", r.getNights());
        m.put("status", r.getStatus());
        return m;
    }

    private static Map<String, Object> mapBill(BillResult b) {
        Map<String, Object> m = new HashMap<>();
        m.put("subTotal", b.getSubTotal());
        m.put("tax", b.getTax());
        m.put("discount", b.getDiscount());
        m.put("total", b.getTotal());
        m.put("isLoyalty", b.isLoyalty());
        m.put("nights", b.getNights());
        m.put("nightlyRate", b.getNightlyRate());
        m.put("roomType", nullToEmpty(b.getRoomType()));
        m.put("roomNumber", nullToEmpty(b.getRoomNumber()));
        return m;
    }

    private static Map<String, Object> mapRoom(Room r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("roomNumber", r.getRoomNumber());
        m.put("roomType", r.getRoomType());
        m.put("floor", r.getFloor());
        m.put("maxOccupancy", r.getMaxOccupancy());
        m.put("nightlyRate", r.getNightlyRate());
        m.put("view", nullToEmpty(r.getViewDesc()));
        m.put("amenities", r.getAmenities());
        m.put("isAvailable", r.isAvailable());
        m.put("roomStatus", r.getRoomStatus());
        return m;
    }

    private static Map<String, Object> mapGuest(ocean.view.resort.model.Guest g) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", g.getId());
        m.put("fullName", g.getFullName());
        m.put("email", nullToEmpty(g.getEmail()));
        m.put("phone", nullToEmpty(g.getPhone()));
        m.put("address", nullToEmpty(g.getAddress()));
        m.put("nationality", nullToEmpty(g.getNationality()));
        m.put("notes", nullToEmpty(g.getNotes()));
        m.put("createdAt", g.getCreatedAt() != null ? g.getCreatedAt().toString() : "");
        return m;
    }

    private static Map<String, Object> mapExtraCharge(ocean.view.resort.model.ExtraCharge e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("reservationId", e.getReservationId());
        m.put("description", e.getDescription());
        m.put("amount", e.getAmount());
        m.put("chargedAt", e.getChargedAt() != null ? e.getChargedAt().toString() : "");
        m.put("createdBy", nullToEmpty(e.getCreatedBy()));
        return m;
    }

    private static Map<String, Object> mapMaintenance(ocean.view.resort.model.MaintenanceIssue i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("roomId", i.getRoomId());
        m.put("title", i.getTitle());
        m.put("description", nullToEmpty(i.getDescription()));
        m.put("category", nullToEmpty(i.getCategory()));
        m.put("status", nullToEmpty(i.getStatus()));
        m.put("assignedTo", nullToEmpty(i.getAssignedTo()));
        m.put("reportedAt", i.getReportedAt() != null ? i.getReportedAt().toString() : "");
        m.put("resolvedAt", i.getResolvedAt() != null ? i.getResolvedAt().toString() : "");
        return m;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static class ApiError {
        final int status;
        final Map<String, Object> body;
        ApiError(int status, Map<String, Object> body) { this.status = status; this.body = body; }
    }
}