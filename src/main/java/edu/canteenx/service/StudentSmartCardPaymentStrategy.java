package edu.canteenx.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class StudentSmartCardPaymentStrategy implements PaymentStrategy {
    @Override
    public String getMethodName() {
        return "SMART_CARD";
    }

    @Override
    public PaymentResult process(long orderId, BigDecimal amount) {
        String ref = "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, "DEMO_PAID", ref, "Student RFID Smart Card balance deducted (simulated).");
    }
}
