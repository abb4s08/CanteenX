package edu.canteenx.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record OrderReceipt(long id,String token,String canteen,BigDecimal total,String status,LocalDateTime createdAt) {}
