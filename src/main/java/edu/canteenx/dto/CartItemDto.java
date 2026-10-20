package edu.canteenx.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CartItemDto {
    @NotNull(message = "Item ID is required")
    private Long itemId;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 50, message = "Quantity cannot exceed 50 per dish")
    private int quantity;

    public CartItemDto() {}
    public CartItemDto(Long itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
