package edu.canteenx.service;

import edu.canteenx.dto.*;
import edu.canteenx.model.*;
import edu.canteenx.model.MenuItem;
import edu.canteenx.util.DBConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Application Service.
 * Implements authorization, ACID database transactions, trusted server-side price calculations,
 * atomic token redemption, polymorphic payment handling, and audit logging.
 */
@Service
public class CanteenService {
    public record SalesSummary(long orders, long pending, long redeemed, BigDecimal revenue) {}
    public record TopDish(String name, long quantity, BigDecimal revenue) {}
    public record CanteenMetric(String name, long orders, BigDecimal revenue) {}
    public record TokenDetails(String token, String canteen, String student, BigDecimal total, String status, List<String> lines) {}

    // Active session sets (Swing identity map + Web bearer token map)
    private final Set<User> activeSessions = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Map<String, User> webTokenSessions = new ConcurrentHashMap<>();

    private final Map<String, PaymentStrategy> paymentStrategies = new HashMap<>();
    private AuditService auditService;

    public CanteenService() {}

    @Autowired(required = false)
    public void setPaymentStrategies(List<PaymentStrategy> strategies) {
        if (strategies != null) {
            for (PaymentStrategy s : strategies) {
                paymentStrategies.put(s.getMethodName(), s);
            }
        }
    }

    @Autowired(required = false)
    public void setAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    /* -------------------------------------------------------------
     * Session and Security Management
     * ------------------------------------------------------------- */
    public void requireRole(User user, String role) {
        if (user == null || !role.equals(user.roleName())) {
            throw new SecurityException("Access denied: action not permitted for your role.");
        }
    }

    public String createWebSession(User user) {
        String token = "CTX-" + UUID.randomUUID().toString().replace("-", "");
        webTokenSessions.put(token, user);
        activeSessions.add(user);
        return token;
    }

    public User getUserByWebToken(String token) {
        if (token == null || token.isBlank()) return null;
        if (token.startsWith("Bearer ")) token = token.substring(7).trim();
        return webTokenSessions.get(token);
    }

    public void logout(User user) {
        if (user != null) {
            activeSessions.remove(user);
            webTokenSessions.values().removeIf(u -> u.getId() == user.getId());
        }
    }

    public void logoutWebToken(String token) {
        if (token != null) {
            if (token.startsWith("Bearer ")) token = token.substring(7).trim();
            User u = webTokenSessions.remove(token);
            if (u != null) activeSessions.remove(u);
        }
    }

    /* -------------------------------------------------------------
     * Health and Connectivity
     * ------------------------------------------------------------- */
    public boolean databaseReady() {
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT 1")) {
            return r.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public HealthResponse getHealthStatus() {
        boolean ok = databaseReady();
        int count = 0;
        if (ok) {
            try (Connection c = DBConnection.getConnection();
                 Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT COUNT(*) FROM canteens WHERE is_active=TRUE")) {
                if (r.next()) count = r.getInt(1);
            } catch (SQLException ignored) {}
        }
        return new HealthResponse(
            ok ? "UP" : "DOWN",
            ok ? "CONNECTED" : "OFFLINE",
            ok ? "CanteenX Backend UP — MySQL Connected" : "Backend degraded: unable to connect to MySQL database",
            count
        );
    }

    /* -------------------------------------------------------------
     * Authentication and Identity
     * ------------------------------------------------------------- */
    public User login(String email, String password) throws SQLException {
        String q = "SELECT user_id, name, email, password_hash, role, canteen_id FROM users WHERE email=? AND is_active=TRUE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setString(1, email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || !PasswordUtil.verify(password, r.getString("password_hash"))) {
                    throw new SecurityException("Email or password is incorrect.");
                }
                long id = r.getLong("user_id");
                Long cid = r.getObject("canteen_id") != null ? r.getLong("canteen_id") : null;
                String name = r.getString("name");
                String em = r.getString("email");
                User authenticated = switch (r.getString("role")) {
                    case "STUDENT" -> new Student(id, name, em);
                    case "SHOPKEEPER" -> new Shopkeeper(id, name, em, cid != null ? cid : 0);
                    case "ADMIN" -> new Admin(id, name, em);
                    default -> throw new SecurityException("Account role is not supported.");
                };
                activeSessions.add(authenticated);
                if (auditService != null) {
                    auditService.logEvent(id, cid, null, "USER_LOGIN", "User logged in: " + em, "SUCCESS");
                }
                return authenticated;
            }
        }
    }

    public Student registerStudent(String name, String email, String password) throws SQLException {
        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.length() < 6) {
            throw new IllegalArgumentException("Valid name, email, and password (at least 6 characters) required.");
        }
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        String cleanName = name.trim();

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Check existing
                try (PreparedStatement check = c.prepareStatement("SELECT user_id FROM users WHERE email=?")) {
                    check.setString(1, cleanEmail);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalArgumentException("An account with this email already exists.");
                        }
                    }
                }

                long userId;
                String hash = PasswordUtil.hash(password);
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO users(name, email, password_hash, role, canteen_id, is_active) VALUES(?, ?, ?, 'STUDENT', NULL, TRUE)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, cleanName);
                    insert.setString(2, cleanEmail);
                    insert.setString(3, hash);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Failed to retrieve generated user ID.");
                        userId = keys.getLong(1);
                    }
                }
                c.commit();
                Student student = new Student(userId, cleanName, cleanEmail);
                if (auditService != null) {
                    auditService.logEvent(userId, null, null, "USER_REGISTERED", "New student registered: " + cleanEmail, "SUCCESS");
                }
                return student;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                if (e instanceof RuntimeException re) throw re;
                throw new SQLException(e);
            }
        }
    }

    public void ensureDemoAccounts() throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO users(name,email,password_hash,role,canteen_id) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)")) {
                addDemo(p, "Aarav Student", "student@canteenx.local", "student123", "STUDENT", null);
                addDemo(p, "REC Cafe Counter", "cafe@canteenx.local", "cafe123", "SHOPKEEPER", canteenId(c, "CAF"));
                addDemo(p, "REC Hut Counter", "hut@canteenx.local", "hut123", "SHOPKEEPER", canteenId(c, "HUT"));
                addDemo(p, "Campus Admin", "admin@canteenx.local", "admin123", "ADMIN", null);
                p.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private long canteenId(Connection c, String prefix) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT canteen_id FROM canteens WHERE token_prefix=?")) {
            p.setString(1, prefix);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Canteen prefix " + prefix + " missing; run schema and seed.");
                return r.getLong(1);
            }
        }
    }

    private void addDemo(PreparedStatement p, String name, String email, String pw, String role, Long cid) throws SQLException {
        p.setString(1, name);
        p.setString(2, email);
        p.setString(3, PasswordUtil.hash(pw));
        p.setString(4, role);
        if (cid == null) p.setNull(5, Types.BIGINT); else p.setLong(5, cid);
        p.addBatch();
    }

    /* -------------------------------------------------------------
     * Canteen Discovery and Management
     * ------------------------------------------------------------- */
    public List<Canteen> canteens() throws SQLException {
        String q = "SELECT canteen_id, name, token_prefix, is_active FROM canteens WHERE is_active=TRUE ORDER BY name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<Canteen> out = new ArrayList<>();
            while (r.next()) {
                out.add(new Canteen(r.getLong(1), r.getString(2), r.getString(3), r.getBoolean(4)));
            }
            return out;
        }
    }

    public List<Map<String, Object>> getAllCanteensDetailed() throws SQLException {
        String q = """
            SELECT c.canteen_id, c.name, c.token_prefix, c.is_active,
                   COUNT(DISTINCT m.item_id) as total_items,
                   COUNT(DISTINCT CASE WHEN m.is_available=TRUE AND m.is_deleted=FALSE THEN m.item_id END) as available_items
            FROM canteens c
            LEFT JOIN menu_items m ON c.canteen_id = m.canteen_id
            GROUP BY c.canteen_id, c.name, c.token_prefix, c.is_active
            ORDER BY c.name
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> out = new ArrayList<>();
            while (r.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("canteenId", r.getLong(1));
                map.put("name", r.getString(2));
                map.put("tokenPrefix", r.getString(3));
                map.put("active", r.getBoolean(4));
                map.put("totalItems", r.getLong(5));
                map.put("availableItems", r.getLong(6));
                out.add(map);
            }
            return out;
        }
    }

    public Canteen getCanteenById(long canteenId) throws SQLException {
        String q = "SELECT canteen_id, name, token_prefix, is_active FROM canteens WHERE canteen_id=?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, canteenId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Canteen not found with ID: " + canteenId);
                return new Canteen(r.getLong(1), r.getString(2), r.getString(3), r.getBoolean(4));
            }
        }
    }

    public Canteen createCanteen(Admin admin, String name, String tokenPrefix) throws SQLException {
        requireRole(admin, "ADMIN");
        if (name == null || name.isBlank() || tokenPrefix == null || tokenPrefix.isBlank()) {
            throw new IllegalArgumentException("Canteen name and 3-character token prefix are required.");
        }
        String cleanName = name.trim();
        String cleanPrefix = tokenPrefix.trim().toUpperCase(Locale.ROOT);
        if (cleanPrefix.length() < 2 || cleanPrefix.length() > 4) {
            throw new IllegalArgumentException("Token prefix must be 2 to 4 characters.");
        }

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                long cid;
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO canteens(name, token_prefix, is_active) VALUES(?, ?, TRUE)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, cleanName);
                    p.setString(2, cleanPrefix);
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        keys.next();
                        cid = keys.getLong(1);
                    }
                }
                // Initialize token counter
                try (PreparedStatement cp = c.prepareStatement(
                        "INSERT INTO token_counters(canteen_id, `last_value`) VALUES(?, 0)")) {
                    cp.setLong(1, cid);
                    cp.executeUpdate();
                }
                c.commit();
                if (auditService != null) {
                    auditService.logEvent(admin.getId(), cid, null, "CANTEEN_CREATED", "New canteen created: " + cleanName + " (" + cleanPrefix + ")", "SUCCESS");
                }
                return new Canteen(cid, cleanName, cleanPrefix, true);
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException(e);
            }
        }
    }

    /* -------------------------------------------------------------
     * Menu Administration and Browsing
     * ------------------------------------------------------------- */
    public List<MenuItem> menu(long canteenId, boolean includeUnavailable) throws SQLException {
        String q = "SELECT m.item_id, m.canteen_id, c.name, m.name, m.category, m.price, m.is_available " +
                   "FROM menu_items m JOIN canteens c USING(canteen_id) " +
                   "WHERE m.canteen_id=? AND m.is_deleted=FALSE " +
                   (includeUnavailable ? "" : "AND m.is_available=TRUE ") +
                   "ORDER BY m.category, m.name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, canteenId);
            try (ResultSet r = p.executeQuery()) {
                List<MenuItem> a = new ArrayList<>();
                while (r.next()) {
                    a.add(new MenuItem(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getBigDecimal(6), r.getBoolean(7)));
                }
                return a;
            }
        }
    }

    public List<MenuItem> allMenuItems(boolean includeUnavailable) throws SQLException {
        String q = "SELECT m.item_id, m.canteen_id, c.name, m.name, m.category, m.price, m.is_available " +
                   "FROM menu_items m JOIN canteens c USING(canteen_id) " +
                   "WHERE m.is_deleted=FALSE " +
                   (includeUnavailable ? "" : "AND m.is_available=TRUE ") +
                   "ORDER BY c.name, m.category, m.name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<MenuItem> a = new ArrayList<>();
            while (r.next()) {
                a.add(new MenuItem(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getBigDecimal(6), r.getBoolean(7)));
            }
            return a;
        }
    }

    public void saveMenuItem(Admin admin, Long id, long canteenId, String name, String category, BigDecimal price, boolean available) throws SQLException {
        requireRole(admin, "ADMIN");
        if (name == null || name.isBlank() || category == null || category.isBlank() || price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Enter a name, category, and non-negative price.");
        }
        String q = id == null
            ? "INSERT INTO menu_items(canteen_id, name, category, price, is_available) VALUES(?, ?, ?, ?, ?)"
            : "UPDATE menu_items SET canteen_id=?, name=?, category=?, price=?, is_available=? WHERE item_id=? AND is_deleted=FALSE";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, canteenId);
            p.setString(2, name.trim());
            p.setString(3, category.trim());
            p.setBigDecimal(4, price);
            p.setBoolean(5, available);
            if (id != null) p.setLong(6, id);
            p.executeUpdate();
            if (auditService != null) {
                auditService.logEvent(admin.getId(), canteenId, null, id == null ? "MENU_ITEM_CREATED" : "MENU_ITEM_UPDATED",
                        "Menu item " + (id == null ? "created" : "updated") + ": " + name.trim() + " (₹" + price + ")", "SUCCESS");
            }
        }
    }

    public void deleteMenuItem(Admin admin, long id) throws SQLException {
        requireRole(admin, "ADMIN");
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE menu_items SET is_deleted=TRUE, is_available=FALSE WHERE item_id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
            if (auditService != null) {
                auditService.logEvent(admin.getId(), null, null, "MENU_ITEM_ARCHIVED", "Menu item archived (ID: " + id + ")", "SUCCESS");
            }
        }
    }

    /* -------------------------------------------------------------
     * Order Placement and Concurrency-Protected Token Allocation
     * ------------------------------------------------------------- */
    public String placeOrder(Student student, long canteenId, List<CartLine> cart, String paymentMethod) throws SQLException {
        OrderResponse resp = placeOrderDetailed(student, canteenId, cart, paymentMethod);
        return resp.getTokenCode();
    }

    public OrderResponse placeOrderDetailed(Student student, long canteenId, List<CartLine> cart, String paymentMethod) throws SQLException {
        requireRole(student, "STUDENT");
        if (cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException("Your shopping cart is empty.");
        }
        if (paymentMethod == null || !Set.of("CAMPUS_UPI", "SMART_CARD", "CASH").contains(paymentMethod)) {
            throw new IllegalArgumentException("Choose a valid payment method (CAMPUS_UPI, SMART_CARD, or CASH).");
        }

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Validate canteen and retrieve token prefix
                String prefix;
                String canteenName;
                try (PreparedStatement p = c.prepareStatement("SELECT name, token_prefix FROM canteens WHERE canteen_id=? AND is_active=TRUE")) {
                    p.setLong(1, canteenId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("Selected canteen outlet is currently unavailable.");
                        canteenName = r.getString(1);
                        prefix = r.getString(2);
                    }
                }

                // 2. Lock token counter row atomically with SELECT ... FOR UPDATE
                long sequence;
                try (PreparedStatement p = c.prepareStatement("SELECT `last_value` FROM token_counters WHERE canteen_id=? FOR UPDATE")) {
                    p.setLong(1, canteenId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("Token counter missing for canteen ID: " + canteenId + ". Run database seed.");
                        sequence = r.getLong(1) + 1;
                    }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE token_counters SET `last_value`=? WHERE canteen_id=?")) {
                    p.setLong(1, sequence);
                    p.setLong(2, canteenId);
                    p.executeUpdate();
                }

                // 3. Authoritatively load prices from MySQL and calculate authoritative total
                record Priced(CartLine line, String name, BigDecimal price) {}
                List<Priced> priced = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (CartLine line : cart) {
                    if (line.quantity() < 1 || line.quantity() > 50) {
                        throw new IllegalArgumentException("Quantity must be between 1 and 50.");
                    }
                    try (PreparedStatement p = c.prepareStatement(
                            "SELECT name, price FROM menu_items WHERE item_id=? AND canteen_id=? AND is_available=TRUE AND is_deleted=FALSE")) {
                        p.setLong(1, line.itemId());
                        p.setLong(2, canteenId);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) throw new SQLException("A selected dish is no longer available in this outlet.");
                            BigDecimal price = r.getBigDecimal(2);
                            priced.add(new Priced(line, r.getString(1), price));
                            total = total.add(price.multiply(BigDecimal.valueOf(line.quantity())));
                        }
                    }
                }

                // 4. Generate unique sequential token code e.g. CAF-0000001
                String token = String.format(Locale.ROOT, "%s-%07d", prefix, sequence);
                long orderId;

                // 5. Insert order row
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO orders(user_id, canteen_id, token_code, total_amount, payment_method, payment_status, order_status) " +
                        "VALUES(?, ?, ?, ?, ?, 'DEMO_PAID', 'PLACED')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1, student.getId());
                    p.setLong(2, canteenId);
                    p.setString(3, token);
                    p.setBigDecimal(4, total);
                    p.setString(5, paymentMethod);
                    p.executeUpdate();
                    try (ResultSet k = p.getGeneratedKeys()) {
                        if (!k.next()) throw new SQLException("Failed to retrieve generated order ID.");
                        orderId = k.getLong(1);
                    }
                }

                // 6. Insert order items snapshot
                List<OrderItemDto> itemDtos = new ArrayList<>();
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO order_items(order_id, item_id, item_name_snapshot, quantity, unit_price) VALUES(?, ?, ?, ?, ?)")) {
                    for (Priced x : priced) {
                        p.setLong(1, orderId);
                        p.setLong(2, x.line().itemId());
                        p.setString(3, x.name());
                        p.setInt(4, x.line().quantity());
                        p.setBigDecimal(5, x.price());
                        p.addBatch();

                        BigDecimal lineTotal = x.price().multiply(BigDecimal.valueOf(x.line().quantity()));
                        itemDtos.add(new OrderItemDto(x.line().itemId(), x.name(), x.line().quantity(), x.price(), lineTotal));
                    }
                    p.executeBatch();
                }

                // 7. Polymorphic payment processing and payment transaction recording
                PaymentStrategy strategy = paymentStrategies.get(paymentMethod);
                PaymentStrategy.PaymentResult paymentResult;
                if (strategy != null) {
                    paymentResult = strategy.process(orderId, total);
                } else {
                    paymentResult = new PaymentStrategy.PaymentResult(true, "DEMO_PAID", "SIM-" + UUID.randomUUID().toString().substring(0, 8), "Simulated payment");
                }

                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO payment_transactions(order_id, payment_method, amount, payment_status, provider_reference) VALUES(?, ?, ?, ?, ?)")) {
                    p.setLong(1, orderId);
                    p.setString(2, paymentMethod);
                    p.setBigDecimal(3, total);
                    p.setString(4, paymentResult.status());
                    p.setString(5, paymentResult.reference());
                    p.executeUpdate();
                }

                // 8. Commit ACID transaction
                c.commit();

                // 9. Audit event
                if (auditService != null) {
                    auditService.logEvent(student.getId(), canteenId, orderId, "ORDER_PLACED",
                            "Order placed with token: " + token + ", total: ₹" + total + " via " + paymentMethod, "SUCCESS");
                }

                return new OrderResponse(
                    orderId, token, canteenId, canteenName, total, paymentMethod, "DEMO_PAID", "PLACED",
                    LocalDateTime.now(), null, itemDtos
                );
            } catch (Exception e) {
                c.rollback();
                if (auditService != null) {
                    auditService.logEvent(student.getId(), canteenId, null, "ORDER_FAILED",
                            "Order creation failed: " + e.getMessage(), "FAILURE");
                }
                if (e instanceof SQLException se) throw se;
                if (e instanceof RuntimeException re) throw re;
                throw new SQLException(e);
            }
        }
    }

    /* -------------------------------------------------------------
     * Order Query and History
     * ------------------------------------------------------------- */
    public List<OrderReceipt> history(Student student) throws SQLException {
        requireRole(student, "STUDENT");
        String q = "SELECT o.order_id, o.token_code, c.name, o.total_amount, o.order_status, o.created_at " +
                   "FROM orders o JOIN canteens c USING(canteen_id) " +
                   "WHERE o.user_id=? ORDER BY o.created_at DESC LIMIT 100";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, student.getId());
            try (ResultSet r = p.executeQuery()) {
                List<OrderReceipt> a = new ArrayList<>();
                while (r.next()) {
                    a.add(new OrderReceipt(r.getLong(1), r.getString(2), r.getString(3), r.getBigDecimal(4), r.getString(5), r.getTimestamp(6).toLocalDateTime()));
                }
                return a;
            }
        }
    }

    public List<OrderResponse> getDetailedOrdersForStudent(Student student) throws SQLException {
        requireRole(student, "STUDENT");
        String q = """
            SELECT o.order_id, o.token_code, o.canteen_id, c.name as canteen_name,
                   o.total_amount, o.payment_method, o.payment_status, o.order_status,
                   o.created_at, o.redeemed_at
            FROM orders o
            JOIN canteens c ON o.canteen_id = c.canteen_id
            WHERE o.user_id=?
            ORDER BY o.created_at DESC
            LIMIT 100
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, student.getId());
            try (ResultSet r = p.executeQuery()) {
                List<OrderResponse> list = new ArrayList<>();
                while (r.next()) {
                    long orderId = r.getLong(1);
                    List<OrderItemDto> items = getOrderItems(c, orderId);
                    list.add(new OrderResponse(
                        orderId,
                        r.getString(2),
                        r.getLong(3),
                        r.getString(4),
                        r.getBigDecimal(5),
                        r.getString(6),
                        r.getString(7),
                        r.getString(8),
                        r.getTimestamp(9).toLocalDateTime(),
                        r.getTimestamp(10) != null ? r.getTimestamp(10).toLocalDateTime() : null,
                        items
                    ));
                }
                return list;
            }
        }
    }

    public OrderResponse getOrderById(long orderId, User caller) throws SQLException {
        String q = """
            SELECT o.order_id, o.token_code, o.canteen_id, c.name as canteen_name,
                   o.total_amount, o.payment_method, o.payment_status, o.order_status,
                   o.created_at, o.redeemed_at, o.user_id
            FROM orders o
            JOIN canteens c ON o.canteen_id = c.canteen_id
            WHERE o.order_id=?
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setLong(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Order not found with ID: " + orderId);
                long ownerId = r.getLong("user_id");
                long canteenId = r.getLong("canteen_id");

                // Ownership / Authorization verification
                if (caller instanceof Student && caller.getId() != ownerId) {
                    throw new SecurityException("Unauthorized: you cannot view another student's order.");
                }
                if (caller instanceof Shopkeeper s && s.getCanteenId() != canteenId) {
                    throw new SecurityException("Unauthorized: this order belongs to another canteen.");
                }

                List<OrderItemDto> items = getOrderItems(c, orderId);
                return new OrderResponse(
                    orderId,
                    r.getString(2),
                    canteenId,
                    r.getString(4),
                    r.getBigDecimal(5),
                    r.getString(6),
                    r.getString(7),
                    r.getString(8),
                    r.getTimestamp(9).toLocalDateTime(),
                    r.getTimestamp(10) != null ? r.getTimestamp(10).toLocalDateTime() : null,
                    items
                );
            }
        }
    }

    private List<OrderItemDto> getOrderItems(Connection c, long orderId) throws SQLException {
        List<OrderItemDto> items = new ArrayList<>();
        try (PreparedStatement ip = c.prepareStatement(
                "SELECT item_id, item_name_snapshot, quantity, unit_price FROM order_items WHERE order_id=?")) {
            ip.setLong(1, orderId);
            try (ResultSet ir = ip.executeQuery()) {
                while (ir.next()) {
                    Long itemId = ir.getObject(1) != null ? ir.getLong(1) : null;
                    String name = ir.getString(2);
                    int qty = ir.getInt(3);
                    BigDecimal unitPrice = ir.getBigDecimal(4);
                    items.add(new OrderItemDto(itemId, name, qty, unitPrice, unitPrice.multiply(BigDecimal.valueOf(qty))));
                }
            }
        }
        return items;
    }

    /* -------------------------------------------------------------
     * Shopkeeper Token Verification and Atomic Redemption
     * ------------------------------------------------------------- */
    public TokenDetails inspectToken(Shopkeeper shop, String token) throws SQLException {
        requireRole(shop, "SHOPKEEPER");
        TokenVerificationResponse r = inspectTokenDetailed(shop, token);
        List<String> lines = new ArrayList<>();
        for (OrderItemDto item : r.getItems()) {
            lines.add(item.getItemName() + " × " + item.getQuantity() + " · ₹" + item.getUnitPrice());
        }
        return new TokenDetails(r.getTokenCode(), r.getCanteenName(), r.getStudentName(), r.getTotalAmount(), r.getOrderStatus(), lines);
    }

    public TokenVerificationResponse inspectTokenDetailed(Shopkeeper shop, String token) throws SQLException {
        requireRole(shop, "SHOPKEEPER");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token code is required.");
        }
        String cleanToken = token.trim().toUpperCase(Locale.ROOT);

        String q = """
            SELECT o.order_id, o.token_code, c.canteen_id, c.name as canteen_name,
                   u.name as student_name, u.email as student_email,
                   o.total_amount, o.order_status, o.payment_status, o.payment_method,
                   o.created_at, o.redeemed_at
            FROM orders o
            JOIN canteens c USING(canteen_id)
            JOIN users u ON u.user_id=o.user_id
            WHERE o.token_code=?
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q)) {
            p.setString(1, cleanToken);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    throw new NoSuchElementException("Token not found: " + cleanToken);
                }
                long orderCanteenId = r.getLong("canteen_id");
                if (orderCanteenId != shop.getCanteenId()) {
                    throw new SecurityException("Access Denied: This token belongs to another outlet (" + r.getString("canteen_name") + ").");
                }
                String paymentStatus = r.getString("payment_status");
                if (!"DEMO_PAID".equals(paymentStatus)) {
                    throw new SecurityException("Payment is not confirmed for this token (status: " + paymentStatus + ").");
                }

                long orderId = r.getLong("order_id");
                List<OrderItemDto> items = getOrderItems(c, orderId);
                String orderStatus = r.getString("order_status");
                boolean eligible = "PLACED".equals(orderStatus) && "DEMO_PAID".equals(paymentStatus);
                String msg = switch (orderStatus) {
                    case "PLACED" -> "VALID — PAID — READY FOR HANDOVER";
                    case "REDEEMED" -> "ALREADY REDEEMED";
                    case "CANCELLED" -> "ORDER CANCELLED";
                    default -> "UNVERIFIED STATUS: " + orderStatus;
                };

                return new TokenVerificationResponse(
                    cleanToken,
                    orderId,
                    orderCanteenId,
                    r.getString("canteen_name"),
                    r.getString("student_name"),
                    r.getString("student_email"),
                    r.getBigDecimal("total_amount"),
                    r.getString("payment_method"),
                    paymentStatus,
                    orderStatus,
                    r.getTimestamp("created_at").toLocalDateTime(),
                    r.getTimestamp("redeemed_at") != null ? r.getTimestamp("redeemed_at").toLocalDateTime() : null,
                    items,
                    eligible,
                    msg
                );
            }
        }
    }

    public void redeem(Shopkeeper shop, String token) throws SQLException {
        redeemDetailed(shop, token);
    }

    public TokenVerificationResponse redeemDetailed(Shopkeeper shop, String token) throws SQLException {
        requireRole(shop, "SHOPKEEPER");
        TokenVerificationResponse details = inspectTokenDetailed(shop, token);
        if ("REDEEMED".equals(details.getOrderStatus())) {
            throw new IllegalStateException("This token has already been redeemed.");
        }
        if (!"PLACED".equals(details.getOrderStatus())) {
            throw new IllegalStateException("This order is not eligible for redemption (status: " + details.getOrderStatus() + ").");
        }

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Conditional atomic database update
                int updated;
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE orders SET order_status='REDEEMED', redeemed_at=CURRENT_TIMESTAMP " +
                        "WHERE token_code=? AND canteen_id=? AND order_status='PLACED' AND payment_status='DEMO_PAID'")) {
                    p.setString(1, details.getTokenCode());
                    p.setLong(2, shop.getCanteenId());
                    updated = p.executeUpdate();
                }
                if (updated != 1) {
                    throw new IllegalStateException("Redemption conflict: token was already redeemed concurrently or is not eligible.");
                }

                // Insert into redemption_logs table
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO redemption_logs(order_id, redeemed_by) SELECT order_id, ? FROM orders WHERE token_code=?")) {
                    p.setLong(1, shop.getId());
                    p.setString(2, details.getTokenCode());
                    p.executeUpdate();
                }

                c.commit();

                if (auditService != null) {
                    auditService.logEvent(shop.getId(), shop.getCanteenId(), details.getOrderId(), "TOKEN_REDEEMED",
                            "Token " + details.getTokenCode() + " redeemed by staff " + shop.getName(), "SUCCESS");
                }

                return inspectTokenDetailed(shop, token);
            } catch (Exception e) {
                c.rollback();
                if (auditService != null) {
                    auditService.logEvent(shop.getId(), shop.getCanteenId(), details.getOrderId(), "REDEEM_FAILED",
                            "Redemption failed for token " + details.getTokenCode() + ": " + e.getMessage(), "FAILURE");
                }
                if (e instanceof SQLException se) throw se;
                if (e instanceof RuntimeException re) throw re;
                throw new SQLException(e);
            }
        }
    }

    /* -------------------------------------------------------------
     * Administrator Analytics and Reporting
     * ------------------------------------------------------------- */
    public SalesSummary summary(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        String q = "SELECT COUNT(*) orders, " +
                   "COALESCE(SUM(order_status='PLACED'), 0) pending, " +
                   "COALESCE(SUM(order_status='REDEEMED'), 0) redeemed, " +
                   "COALESCE(SUM(CASE WHEN payment_status='DEMO_PAID' AND order_status<>'CANCELLED' THEN total_amount ELSE 0 END), 0) revenue " +
                   "FROM orders";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            r.next();
            return new SalesSummary(r.getLong(1), r.getLong(2), r.getLong(3), r.getBigDecimal(4));
        }
    }

    public List<CanteenMetric> canteenMetrics(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        String q = "SELECT c.name, COUNT(o.order_id), " +
                   "COALESCE(SUM(CASE WHEN o.payment_status='DEMO_PAID' AND o.order_status<>'CANCELLED' THEN o.total_amount ELSE 0 END), 0) " +
                   "FROM canteens c LEFT JOIN orders o USING(canteen_id) " +
                   "GROUP BY c.canteen_id, c.name ORDER BY c.name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<CanteenMetric> a = new ArrayList<>();
            while (r.next()) {
                a.add(new CanteenMetric(r.getString(1), r.getLong(2), r.getBigDecimal(3)));
            }
            return a;
        }
    }

    public List<TopDish> topDishes(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        String q = "SELECT oi.item_name_snapshot, SUM(oi.quantity), SUM(oi.quantity*oi.unit_price) " +
                   "FROM order_items oi JOIN orders o USING(order_id) " +
                   "WHERE o.payment_status='DEMO_PAID' AND o.order_status<>'CANCELLED' " +
                   "GROUP BY oi.item_name_snapshot ORDER BY SUM(oi.quantity) DESC LIMIT 10";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<TopDish> a = new ArrayList<>();
            while (r.next()) {
                a.add(new TopDish(r.getString(1), r.getLong(2), r.getBigDecimal(3)));
            }
            return a;
        }
    }

    public AdminDashboardDto getAdminDashboardData(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        SalesSummary s = summary(admin);

        long cancelled = 0;
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM orders WHERE order_status='CANCELLED'")) {
            if (rs.next()) cancelled = rs.getLong(1);
        }

        List<AdminDashboardDto.CanteenMetricDto> outletMetrics = new ArrayList<>();
        String outletSql = """
            SELECT c.canteen_id, c.name, c.token_prefix, COUNT(o.order_id),
                   COALESCE(SUM(CASE WHEN o.payment_status='DEMO_PAID' AND o.order_status<>'CANCELLED' THEN o.total_amount ELSE 0 END), 0)
            FROM canteens c
            LEFT JOIN orders o ON c.canteen_id = o.canteen_id
            GROUP BY c.canteen_id, c.name, c.token_prefix
            ORDER BY c.name
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(outletSql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                outletMetrics.add(new AdminDashboardDto.CanteenMetricDto(
                    r.getLong(1), r.getString(2), r.getString(3), r.getLong(4), r.getBigDecimal(5)
                ));
            }
        }

        List<AdminDashboardDto.TopDishDto> topDishDtos = new ArrayList<>();
        for (TopDish td : topDishes(admin)) {
            topDishDtos.add(new AdminDashboardDto.TopDishDto(td.name(), td.quantity(), td.revenue()));
        }

        List<OrderResponse> recentOrders = new ArrayList<>();
        String recentSql = """
            SELECT o.order_id, o.token_code, o.canteen_id, c.name, o.total_amount,
                   o.payment_method, o.payment_status, o.order_status, o.created_at, o.redeemed_at
            FROM orders o
            JOIN canteens c USING(canteen_id)
            ORDER BY o.created_at DESC
            LIMIT 15
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(recentSql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                long oid = r.getLong(1);
                recentOrders.add(new OrderResponse(
                    oid, r.getString(2), r.getLong(3), r.getString(4), r.getBigDecimal(5),
                    r.getString(6), r.getString(7), r.getString(8),
                    r.getTimestamp(9).toLocalDateTime(),
                    r.getTimestamp(10) != null ? r.getTimestamp(10).toLocalDateTime() : null,
                    getOrderItems(c, oid)
                ));
            }
        }

        return new AdminDashboardDto(
            s.revenue(),
            s.orders(),
            s.orders() - cancelled,
            s.pending(),
            s.redeemed(),
            cancelled,
            outletMetrics,
            topDishDtos,
            recentOrders
        );
    }

    public List<Map<String, Object>> getAllUsers(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        String q = """
            SELECT u.user_id, u.name, u.email, u.role, u.canteen_id, c.name as canteen_name, u.is_active
            FROM users u
            LEFT JOIN canteens c ON u.canteen_id = c.canteen_id
            ORDER BY u.role, u.name
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (r.next()) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("userId", r.getLong(1));
                u.put("name", r.getString(2));
                u.put("email", r.getString(3));
                u.put("role", r.getString(4));
                u.put("canteenId", r.getObject(5));
                u.put("canteenName", r.getString(6));
                u.put("active", r.getBoolean(7));
                list.add(u);
            }
            return list;
        }
    }

    public String report(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        StringBuilder b = new StringBuilder("Canteen,Orders,Revenue\n");
        String q = "SELECT c.name, COUNT(o.order_id), " +
                   "COALESCE(SUM(CASE WHEN o.payment_status='DEMO_PAID' AND o.order_status<>'CANCELLED' THEN o.total_amount ELSE 0 END), 0) " +
                   "FROM canteens c LEFT JOIN orders o USING(canteen_id) " +
                   "GROUP BY c.canteen_id, c.name ORDER BY c.name";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                b.append(csv(r.getString(1))).append(',').append(r.getLong(2)).append(',').append(r.getBigDecimal(3)).append('\n');
            }
        }
        return b.toString();
    }

    public String exportComprehensiveReportCsv(Admin admin) throws SQLException {
        requireRole(admin, "ADMIN");
        StringBuilder sb = new StringBuilder();
        sb.append("Order ID,Token Code,Canteen,Total Amount,Payment Method,Payment Status,Order Status,Created At,Redeemed At\n");

        String q = """
            SELECT o.order_id, o.token_code, c.name, o.total_amount, o.payment_method,
                   o.payment_status, o.order_status, o.created_at, o.redeemed_at
            FROM orders o
            JOIN canteens c USING(canteen_id)
            ORDER BY o.created_at DESC
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(q);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                sb.append(r.getLong(1)).append(',')
                  .append(csv(r.getString(2))).append(',')
                  .append(csv(r.getString(3))).append(',')
                  .append(r.getBigDecimal(4)).append(',')
                  .append(csv(r.getString(5))).append(',')
                  .append(csv(r.getString(6))).append(',')
                  .append(csv(r.getString(7))).append(',')
                  .append(csv(r.getString(8))).append(',')
                  .append(csv(r.getString(9) != null ? r.getString(9) : "N/A"))
                  .append('\n');
            }
        }
        return sb.toString();
    }

    private String csv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public void exportReport(Admin admin, File target) throws SQLException, IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            w.write(report(admin));
        }
    }
}
