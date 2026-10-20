package edu.canteenx.dto;

public class AuthResponse {
    private String token;
    private long userId;
    private String name;
    private String email;
    private String role;
    private Long canteenId;
    private String canteenName;
    private String dashboardMessage;

    public AuthResponse() {}

    public AuthResponse(String token, long userId, String name, String email, String role, Long canteenId, String canteenName, String dashboardMessage) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.canteenId = canteenId;
        this.canteenName = canteenName;
        this.dashboardMessage = dashboardMessage;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getCanteenId() { return canteenId; }
    public void setCanteenId(Long canteenId) { this.canteenId = canteenId; }
    public String getCanteenName() { return canteenName; }
    public void setCanteenName(String canteenName) { this.canteenName = canteenName; }
    public String getDashboardMessage() { return dashboardMessage; }
    public void setDashboardMessage(String dashboardMessage) { this.dashboardMessage = dashboardMessage; }
}
