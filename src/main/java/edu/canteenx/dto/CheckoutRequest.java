package edu.canteenx.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public class CheckoutRequest {
    @NotNull(message = "Canteen ID is required")
    private Long canteenId;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "CAMPUS_UPI|SMART_CARD|CASH", message = "Payment method must be CAMPUS_UPI, SMART_CARD, or CASH")
    private String paymentMethod;

    @NotEmpty(message = "Cart cannot be empty")
    @Valid
    private List<CartItemDto> items;

    public CheckoutRequest() {}
    public CheckoutRequest(Long canteenId, String paymentMethod, List<CartItemDto> items) {
        this.canteenId = canteenId;
        this.paymentMethod = paymentMethod;
        this.items = items;
    }

    public Long getCanteenId() { return canteenId; }
    public void setCanteenId(Long canteenId) { this.canteenId = canteenId; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public List<CartItemDto> getItems() { return items; }
    public void setItems(List<CartItemDto> items) { this.items = items; }
}
