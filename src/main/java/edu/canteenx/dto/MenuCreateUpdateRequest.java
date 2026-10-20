package edu.canteenx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class MenuCreateUpdateRequest {
    @NotNull(message = "Canteen ID is required")
    private Long canteenId;

    @NotBlank(message = "Dish name is required")
    @Size(min = 2, max = 120, message = "Dish name must be between 2 and 120 characters")
    private String name;

    @NotBlank(message = "Category is required")
    @Size(min = 2, max = 60, message = "Category must be between 2 and 60 characters")
    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal price;

    private boolean available = true;

    public MenuCreateUpdateRequest() {}

    public MenuCreateUpdateRequest(Long canteenId, String name, String category, BigDecimal price, boolean available) {
        this.canteenId = canteenId;
        this.name = name;
        this.category = category;
        this.price = price;
        this.available = available;
    }

    public Long getCanteenId() { return canteenId; }
    public void setCanteenId(Long canteenId) { this.canteenId = canteenId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
