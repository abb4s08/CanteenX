package edu.canteenx.model;
import java.math.BigDecimal;
public record MenuItem(long id,long canteenId,String canteenName,String name,String category,BigDecimal price,boolean available) {}
