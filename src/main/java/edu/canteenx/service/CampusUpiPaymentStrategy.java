package edu.canteenx.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CampusUpiPaymentStrategy implements PaymentStrategy {
    @Override
    public String getMethodName() {
        return "CAMPUS_UPI";
    }

    @Override
    public PaymentResult process(long orderId, BigDecimal amount) {
        String ref = "UPI-REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, "DEMO_PAID", ref, "Instant Campus UPI payment simulated successfully.");
    }
}
