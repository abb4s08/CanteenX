package edu.canteenx.dto;

import java.math.BigDecimal;

public class OrderItemDto {
    private Long itemId;
    private String itemName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItemDto() {}
    public OrderItemDto(Long itemId, String itemName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
