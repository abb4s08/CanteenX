package edu.canteenx.model;

import java.time.LocalDateTime;

public record AuditLog(
    long auditId,
    Long actorUserId,
    String actorName,
    Long canteenId,
    String canteenName,
    Long orderId,
    String eventType,
    String eventDescription,
    String result,
    LocalDateTime createdAt
) {}
