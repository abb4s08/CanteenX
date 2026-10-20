package edu.canteenx.service;

import edu.canteenx.model.AuditLog;
import edu.canteenx.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public void logEvent(Long actorUserId, Long canteenId, Long orderId, String eventType, String description, String result) {
        String sql = "INSERT INTO audit_logs(actor_user_id, canteen_id, order_id, event_type, event_description, result) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (actorUserId != null) ps.setLong(1, actorUserId); else ps.setNull(1, Types.BIGINT);
            if (canteenId != null) ps.setLong(2, canteenId); else ps.setNull(2, Types.BIGINT);
            if (orderId != null) ps.setLong(3, orderId); else ps.setNull(3, Types.BIGINT);
            ps.setString(4, eventType);
            ps.setString(5, description);
            ps.setString(6, result);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to write audit log entry: {}", description, e);
        }
    }

    public List<AuditLog> getRecentLogs(int limit) throws SQLException {
        String sql = """
            SELECT a.audit_id, a.actor_user_id, u.name as actor_name,
                   a.canteen_id, c.name as canteen_name,
                   a.order_id, a.event_type, a.event_description, a.result, a.created_at
            FROM audit_logs a
            LEFT JOIN users u ON a.actor_user_id = u.user_id
            LEFT JOIN canteens c ON a.canteen_id = c.canteen_id
            ORDER BY a.created_at DESC
            LIMIT ?
        """;
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditLog> logs = new ArrayList<>();
                while (rs.next()) {
                    Long actorId = rs.getObject(2) != null ? rs.getLong(2) : null;
                    Long canteenId = rs.getObject(4) != null ? rs.getLong(4) : null;
                    Long orderId = rs.getObject(6) != null ? rs.getLong(6) : null;
                    logs.add(new AuditLog(
                        rs.getLong(1),
                        actorId,
                        rs.getString(3),
                        canteenId,
                        rs.getString(5),
                        orderId,
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9),
                        rs.getTimestamp(10).toLocalDateTime()
                    ));
                }
                return logs;
            }
        }
    }
}
