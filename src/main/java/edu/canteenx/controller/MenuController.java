package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.dto.MenuCreateUpdateRequest;
import edu.canteenx.model.Admin;
import edu.canteenx.model.MenuItem;
import edu.canteenx.model.User;
import edu.canteenx.service.CanteenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MenuController {

    private final CanteenService canteenService;

    public MenuController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @GetMapping("/menu")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getAllMenuItems(
            @RequestParam(value = "includeUnavailable", defaultValue = "false") boolean includeUnavailable) throws SQLException {
        List<MenuItem> items = canteenService.allMenuItems(includeUnavailable);
        return ResponseEntity.ok(ApiResponse.ok("Menu items retrieved", items));
    }

    @PostMapping("/admin/menu")
    public ResponseEntity<ApiResponse<Void>> createMenuItem(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody MenuCreateUpdateRequest request) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Admin admin)) {
            throw new SecurityException("Admin authorization required to create menu items.");
        }

        canteenService.saveMenuItem(admin, null, request.getCanteenId(), request.getName(),
                request.getCategory(), request.getPrice(), request.isAvailable());
        return ResponseEntity.ok(ApiResponse.ok("Menu item created successfully", null));
    }

    @PutMapping("/admin/menu/{id}")
    public ResponseEntity<ApiResponse<Void>> updateMenuItem(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") long id,
            @Valid @RequestBody MenuCreateUpdateRequest request) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Admin admin)) {
            throw new SecurityException("Admin authorization required to edit menu items.");
        }

        canteenService.saveMenuItem(admin, id, request.getCanteenId(), request.getName(),
                request.getCategory(), request.getPrice(), request.isAvailable());
        return ResponseEntity.ok(ApiResponse.ok("Menu item updated successfully", null));
    }

    @DeleteMapping("/admin/menu/{id}")
    public ResponseEntity<ApiResponse<Void>> archiveMenuItem(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") long id) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Admin admin)) {
            throw new SecurityException("Admin authorization required to archive menu items.");
        }

        canteenService.deleteMenuItem(admin, id);
        return ResponseEntity.ok(ApiResponse.ok("Menu item archived successfully (historical receipts preserved)", null));
    }
}
