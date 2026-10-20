package edu.canteenx.controller;

import edu.canteenx.dto.AdminDashboardDto;
import edu.canteenx.dto.ApiResponse;
import edu.canteenx.model.Admin;
import edu.canteenx.model.AuditLog;
import edu.canteenx.model.Canteen;
import edu.canteenx.model.User;
import edu.canteenx.service.AuditService;
import edu.canteenx.service.CanteenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CanteenService canteenService;
    private final AuditService auditService;

    public AdminController(CanteenService canteenService, AuditService auditService) {
        this.canteenService = canteenService;
        this.auditService = auditService;
    }

    private Admin requireAdmin(String authHeader) {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Admin admin)) {
            throw new SecurityException("Administrator authorization required for this endpoint.");
        }
        return admin;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> getDashboard(
            @RequestHeader("Authorization") String authHeader) throws SQLException {
        Admin admin = requireAdmin(authHeader);
        AdminDashboardDto dto = canteenService.getAdminDashboardData(admin);
        return ResponseEntity.ok(ApiResponse.ok("Dashboard KPIs retrieved", dto));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> getAnalytics(
            @RequestHeader("Authorization") String authHeader) throws SQLException {
        Admin admin = requireAdmin(authHeader);
        AdminDashboardDto dto = canteenService.getAdminDashboardData(admin);
        return ResponseEntity.ok(ApiResponse.ok("Analytics data retrieved", dto));
    }

    @GetMapping("/reports/sales.csv")
    public ResponseEntity<byte[]> downloadSalesReportCsv(
            @RequestHeader("Authorization") String authHeader) throws SQLException {
        Admin admin = requireAdmin(authHeader);
        String csvContent = canteenService.exportComprehensiveReportCsv(admin);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"canteenx_sales_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "limit", defaultValue = "50") int limit) throws SQLException {
        requireAdmin(authHeader);
        List<AuditLog> logs = auditService.getRecentLogs(limit);
        return ResponseEntity.ok(ApiResponse.ok("Audit logs retrieved", logs));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllUsers(
            @RequestHeader("Authorization") String authHeader) throws SQLException {
        Admin admin = requireAdmin(authHeader);
        List<Map<String, Object>> users = canteenService.getAllUsers(admin);
        return ResponseEntity.ok(ApiResponse.ok("Users retrieved", users));
    }

    @PostMapping("/canteens")
    public ResponseEntity<ApiResponse<Canteen>> createCanteen(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) throws SQLException {
        Admin admin = requireAdmin(authHeader);
        String name = body.get("name");
        String prefix = body.get("tokenPrefix");
        Canteen created = canteenService.createCanteen(admin, name, prefix);
        return ResponseEntity.ok(ApiResponse.ok("Canteen created successfully", created));
    }
}
