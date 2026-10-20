package edu.canteenx.dto;

import java.time.LocalDateTime;

public class HealthResponse {
    private String status;
    private String database;
    private String message;
    private int activeCanteens;
    private LocalDateTime timestamp;

    public HealthResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public HealthResponse(String status, String database, String message, int activeCanteens) {
        this.status = status;
        this.database = database;
        this.message = message;
        this.activeCanteens = activeCanteens;
        this.timestamp = LocalDateTime.now();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getActiveCanteens() { return activeCanteens; }
    public void setActiveCanteens(int activeCanteens) { this.activeCanteens = activeCanteens; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
