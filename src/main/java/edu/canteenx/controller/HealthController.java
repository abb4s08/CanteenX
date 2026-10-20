package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.dto.HealthResponse;
import edu.canteenx.service.CanteenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final CanteenService canteenService;

    public HealthController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> checkHealth() {
        HealthResponse status = canteenService.getHealthStatus();
        if ("UP".equals(status.getStatus())) {
            return ResponseEntity.ok(ApiResponse.ok("System healthy", status));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(false, status.getMessage(), status));
        }
    }
}
