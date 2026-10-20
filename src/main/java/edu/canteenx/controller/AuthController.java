package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.dto.AuthResponse;
import edu.canteenx.dto.LoginRequest;
import edu.canteenx.dto.RegisterRequest;
import edu.canteenx.model.Canteen;
import edu.canteenx.model.Shopkeeper;
import edu.canteenx.model.Student;
import edu.canteenx.model.User;
import edu.canteenx.service.CanteenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CanteenService canteenService;

    public AuthController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) throws SQLException {
        User user = canteenService.login(request.getEmail(), request.getPassword());
        String token = canteenService.createWebSession(user);

        Long canteenId = null;
        String canteenName = null;
        if (user instanceof Shopkeeper s) {
            canteenId = s.getCanteenId();
            try {
                Canteen c = canteenService.getCanteenById(canteenId);
                canteenName = c.name();
            } catch (Exception ignored) {}
        }

        AuthResponse auth = new AuthResponse(
            token, user.getId(), user.getName(), user.getEmail(), user.roleName(),
            canteenId, canteenName, user.dashboardMessage()
        );
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) throws SQLException {
        Student student = canteenService.registerStudent(request.getName(), request.getEmail(), request.getPassword());
        String token = canteenService.createWebSession(student);

        AuthResponse auth = new AuthResponse(
            token, student.getId(), student.getName(), student.getEmail(), student.roleName(),
            null, null, student.dashboardMessage()
        );
        return ResponseEntity.ok(ApiResponse.ok("Account registered successfully", auth));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = canteenService.getUserByWebToken(authHeader);
        if (user == null) {
            throw new SecurityException("Authentication required. Please sign in.");
        }

        Long canteenId = null;
        String canteenName = null;
        if (user instanceof Shopkeeper s) {
            canteenId = s.getCanteenId();
            try {
                Canteen c = canteenService.getCanteenById(canteenId);
                canteenName = c.name();
            } catch (Exception ignored) {}
        }

        AuthResponse auth = new AuthResponse(
            authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader,
            user.getId(), user.getName(), user.getEmail(), user.roleName(),
            canteenId, canteenName, user.dashboardMessage()
        );
        return ResponseEntity.ok(ApiResponse.ok("Session verified", auth));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        canteenService.logoutWebToken(authHeader);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", null));
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<ApiResponse<String>> seedDemoAccounts() throws SQLException {
        canteenService.ensureDemoAccounts();
        return ResponseEntity.ok(ApiResponse.ok("Demo accounts created/verified idempotently.",
            "Use: student@canteenx.local / student123, cafe@canteenx.local / cafe123, hut@canteenx.local / hut123, admin@canteenx.local / admin123"));
    }
}
