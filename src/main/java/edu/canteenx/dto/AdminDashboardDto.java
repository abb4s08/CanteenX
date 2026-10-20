package edu.canteenx.dto;

import java.math.BigDecimal;
import java.util.List;

public class AdminDashboardDto {
    private BigDecimal campusRevenue;
    private long totalOrders;
    private long paidOrders;
    private long activeUnredeemedTokens;
    private long redeemedOrders;
    private long cancelledOrders;
    private List<CanteenMetricDto> outletMetrics;
    private List<TopDishDto> topDishes;
    private List<OrderResponse> recentOrders;

    public AdminDashboardDto() {}

    public AdminDashboardDto(BigDecimal campusRevenue, long totalOrders, long paidOrders,
                             long activeUnredeemedTokens, long redeemedOrders, long cancelledOrders,
                             List<CanteenMetricDto> outletMetrics, List<TopDishDto> topDishes,
                             List<OrderResponse> recentOrders) {
        this.campusRevenue = campusRevenue;
        this.totalOrders = totalOrders;
        this.paidOrders = paidOrders;
        this.activeUnredeemedTokens = activeUnredeemedTokens;
        this.redeemedOrders = redeemedOrders;
        this.cancelledOrders = cancelledOrders;
        this.outletMetrics = outletMetrics;
        this.topDishes = topDishes;
        this.recentOrders = recentOrders;
    }

    public static record CanteenMetricDto(long canteenId, String canteenName, String tokenPrefix, long totalOrders, BigDecimal revenue) {}
    public static record TopDishDto(String dishName, long quantitySold, BigDecimal totalRevenue) {}

    public BigDecimal getCampusRevenue() { return campusRevenue; }
    public void setCampusRevenue(BigDecimal campusRevenue) { this.campusRevenue = campusRevenue; }
    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }
    public long getPaidOrders() { return paidOrders; }
    public void setPaidOrders(long paidOrders) { this.paidOrders = paidOrders; }
    public long getActiveUnredeemedTokens() { return activeUnredeemedTokens; }
    public void setActiveUnredeemedTokens(long activeUnredeemedTokens) { this.activeUnredeemedTokens = activeUnredeemedTokens; }
    public long getRedeemedOrders() { return redeemedOrders; }
    public void setRedeemedOrders(long redeemedOrders) { this.redeemedOrders = redeemedOrders; }
    public long getCancelledOrders() { return cancelledOrders; }
    public void setCancelledOrders(long cancelledOrders) { this.cancelledOrders = cancelledOrders; }
    public List<CanteenMetricDto> getOutletMetrics() { return outletMetrics; }
    public void setOutletMetrics(List<CanteenMetricDto> outletMetrics) { this.outletMetrics = outletMetrics; }
    public List<TopDishDto> getTopDishes() { return topDishes; }
    public void setTopDishes(List<TopDishDto> topDishes) { this.topDishes = topDishes; }
    public List<OrderResponse> getRecentOrders() { return recentOrders; }
    public void setRecentOrders(List<OrderResponse> recentOrders) { this.recentOrders = recentOrders; }
}
