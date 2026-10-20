package edu.canteenx.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentTransaction(
    long paymentId,
    long orderId,
    String paymentMethod,
    BigDecimal amount,
    String paymentStatus,
    String providerReference,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
