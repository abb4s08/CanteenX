package edu.canteenx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {
    private long orderId;
    private String tokenCode;
    private long canteenId;
    private String canteenName;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
    private List<OrderItemDto> items;

    public OrderResponse() {}

    public OrderResponse(long orderId, String tokenCode, long canteenId, String canteenName,
                         BigDecimal totalAmount, String paymentMethod, String paymentStatus,
                         String orderStatus, LocalDateTime createdAt, LocalDateTime redeemedAt,
                         List<OrderItemDto> items) {
        this.orderId = orderId;
        this.tokenCode = tokenCode;
        this.canteenId = canteenId;
        this.canteenName = canteenName;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.createdAt = createdAt;
        this.redeemedAt = redeemedAt;
        this.items = items;
    }

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public String getTokenCode() { return tokenCode; }
    public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }
    public long getCanteenId() { return canteenId; }
    public void setCanteenId(long canteenId) { this.canteenId = canteenId; }
    public String getCanteenName() { return canteenName; }
    public void setCanteenName(String canteenName) { this.canteenName = canteenName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(LocalDateTime redeemedAt) { this.redeemedAt = redeemedAt; }
    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}
