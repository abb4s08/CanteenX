package edu.canteenx.controller;

import edu.canteenx.dto.ApiResponse;
import edu.canteenx.dto.TokenVerificationResponse;
import edu.canteenx.model.Shopkeeper;
import edu.canteenx.model.User;
import edu.canteenx.service.CanteenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/api/shopkeeper/tokens")
public class TokenController {

    private final CanteenService canteenService;

    public TokenController(CanteenService canteenService) {
        this.canteenService = canteenService;
    }

    @GetMapping("/{tokenCode}")
    public ResponseEntity<ApiResponse<TokenVerificationResponse>> verifyToken(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("tokenCode") String tokenCode) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Shopkeeper shopkeeper)) {
            throw new SecurityException("Counter staff authorization required for token verification.");
        }

        TokenVerificationResponse response = canteenService.inspectTokenDetailed(shopkeeper, tokenCode);
        return ResponseEntity.ok(ApiResponse.ok("Token verified successfully", response));
    }

    @PostMapping("/{tokenCode}/redeem")
    public ResponseEntity<ApiResponse<TokenVerificationResponse>> redeemToken(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("tokenCode") String tokenCode) throws SQLException {
        User user = canteenService.getUserByWebToken(authHeader);
        if (!(user instanceof Shopkeeper shopkeeper)) {
            throw new SecurityException("Counter staff authorization required for token redemption.");
        }

        TokenVerificationResponse response = canteenService.redeemDetailed(shopkeeper, tokenCode);
        return ResponseEntity.ok(ApiResponse.ok("Token redeemed successfully. Handover recorded.", response));
    }
}
