package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.model.Canteen;
import edu.canteenx.model.MenuItem;
import edu.canteenx.service.CanteenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/canteens")
public class CanteenController {

    private final CanteenService canteenService;

    public CanteenController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCanteens() throws SQLException {
        List<Map<String, Object>> list = canteenService.getAllCanteensDetailed();
        return ResponseEntity.ok(ApiResponse.ok("Canteens retrieved", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Canteen>> getCanteenById(@PathVariable("id") long id) throws SQLException {
        Canteen c = canteenService.getCanteenById(id);
        return ResponseEntity.ok(ApiResponse.ok(c));
    }

    @GetMapping("/{id}/menu")
    public ResponseEntity<ApiResponse<List<MenuItem>>> getCanteenMenu(
            @PathVariable("id") long id,
            @RequestParam(value = "includeUnavailable", defaultValue = "false") boolean includeUnavailable) throws SQLException {
        List<MenuItem> items = canteenService.menu(id, includeUnavailable);
        return ResponseEntity.ok(ApiResponse.ok("Menu items retrieved", items));
    }
}
