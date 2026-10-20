package edu.canteenx.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CashPaymentStrategy implements PaymentStrategy {
    @Override
    public String getMethodName() {
        return "CASH";
    }

    @Override
    public PaymentResult process(long orderId, BigDecimal amount) {
        String ref = "CASH-CTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, "DEMO_PAID", ref, "Counter cash token payment registered.");
    }
}
