package edu.canteenx.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TokenVerificationResponse {
    private String tokenCode;
    private long orderId;
    private long canteenId;
    private String canteenName;
    private String studentName;
    private String studentEmail;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
    private List<OrderItemDto> items;
    private boolean eligibleForHandover;
    private String verificationMessage;

    public TokenVerificationResponse() {}

    public TokenVerificationResponse(String tokenCode, long orderId, long canteenId, String canteenName,
                                   String studentName, String studentEmail, BigDecimal totalAmount,
                                   String paymentMethod, String paymentStatus, String orderStatus,
                                   LocalDateTime createdAt, LocalDateTime redeemedAt,
                                   List<OrderItemDto> items, boolean eligibleForHandover,
                                   String verificationMessage) {
        this.tokenCode = tokenCode;
        this.orderId = orderId;
        this.canteenId = canteenId;
        this.canteenName = canteenName;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.createdAt = createdAt;
        this.redeemedAt = redeemedAt;
        this.items = items;
        this.eligibleForHandover = eligibleForHandover;
        this.verificationMessage = verificationMessage;
    }

    public String getTokenCode() { return tokenCode; }
    public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }
    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public long getCanteenId() { return canteenId; }
    public void setCanteenId(long canteenId) { this.canteenId = canteenId; }
    public String getCanteenName() { return canteenName; }
    public void setCanteenName(String canteenName) { this.canteenName = canteenName; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
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
    public boolean isEligibleForHandover() { return eligibleForHandover; }
    public void setEligibleForHandover(boolean eligibleForHandover) { this.eligibleForHandover = eligibleForHandover; }
    public String getVerificationMessage() { return verificationMessage; }
    public void setVerificationMessage(String verificationMessage) { this.verificationMessage = verificationMessage; }
}
