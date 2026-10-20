package edu.canteenx.service;

import java.math.BigDecimal;

/**
 * Common Strategy interface demonstrating OOP Polymorphism for academic evaluation.
 * Different payment channels implement this interface with specific simulation logic.
 */
public interface PaymentStrategy {
    String getMethodName();
    PaymentResult process(long orderId, BigDecimal amount);

    record PaymentResult(boolean success, String status, String reference, String message) {}
}
