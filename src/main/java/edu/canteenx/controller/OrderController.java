package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.dto.CartItemDto;
import edu.canteenx.dto.CheckoutRequest;
import edu.canteenx.dto.OrderResponse;
import edu.canteenx.model.CartLine;
import edu.canteenx.model.Student;
import edu.canteenx.model.User;
import edu.canteenx.service.CanteenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CanteenService canteenService;

    public OrderController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CheckoutRequest request) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Student student)) {
            throw new SecurityException("Student authorization required to place food orders.");
        }

        List<CartLine> cartLines = new ArrayList<>();
        for (CartItemDto item : request.getItems()) {
            cartLines.add(new CartLine(item.getItemId(), item.getQuantity()));
        }

        OrderResponse orderResponse = canteenService.placeOrderDetailed(
                student, request.getCanteenId(), cartLines, request.getPaymentMethod());

        return ResponseEntity.ok(ApiResponse.ok("Order placed successfully. Digital token generated.", orderResponse));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @RequestHeader("Authorization") String authHeader) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Student student)) {
            throw new SecurityException("Student authorization required to view personal orders.");
        }

        List<OrderResponse> orders = canteenService.getDetailedOrdersForStudent(student);
        return ResponseEntity.ok(ApiResponse.ok("Order history retrieved", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") long id) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (user == null) {
            throw new SecurityException("Authentication required.");
        }

        OrderResponse order = canteenService.getOrderById(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Order retrieved", order));
    }
}
