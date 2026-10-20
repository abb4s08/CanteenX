package edu.canteenx;

import edu.canteenx.dto.OrderResponse;
import edu.canteenx.model.*;
import edu.canteenx.model.MenuItem;
import edu.canteenx.service.CanteenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanteenX — Security & Authorization Tests")
public class SecurityAndAuthorizationTest {

    private static CanteenService service;
    private static Student student1;
    private static Student student2;
    private static Shopkeeper cafeShopkeeper;
    private static Admin admin;
    private static Canteen cafe;
    private static Canteen hut;

    @BeforeAll
    static void setUp() throws SQLException {
        service = new CanteenService();
        service.ensureDemoAccounts();

        student1 = (Student) service.login("student@canteenx.local", "student123");
        // Register or retrieve a second student for privacy tests
        try {
            student2 = service.registerStudent("Test Student Two", "student2@canteenx.local", "student123");
        } catch (IllegalArgumentException e) {
            student2 = (Student) service.login("student2@canteenx.local", "student123");
        }

        cafeShopkeeper = (Shopkeeper) service.login("cafe@canteenx.local", "cafe123");
        admin = (Admin) service.login("admin@canteenx.local", "admin123");

        cafe = service.canteens().stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();
        hut = service.canteens().stream().filter(c -> "HUT".equals(c.tokenPrefix())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Student Privacy: Student cannot access another student's order details")
    void testStudentOrderPrivacy() throws SQLException {
        MenuItem cafeItem = service.menu(cafe.id(), false).get(0);
        OrderResponse student1Order = service.placeOrderDetailed(
            student1, cafe.id(), List.of(new CartLine(cafeItem.id(), 1)), "CAMPUS_UPI"
        );

        // Student 1 can view their own order
        assertDoesNotThrow(() -> service.getOrderById(student1Order.getOrderId(), student1));

        // Student 2 attempts to view Student 1's order -> MUST throw SecurityException
        SecurityException ex = assertThrows(SecurityException.class, () ->
            service.getOrderById(student1Order.getOrderId(), student2)
        );
        assertTrue(ex.getMessage().contains("cannot view another student's order"));
    }

    @Test
    @DisplayName("Shopkeeper Outlet Isolation: Counter cannot inspect or redeem cross-canteen tokens")
    void testShopkeeperCrossCanteenIsolation() throws SQLException {
        MenuItem hutItem = service.menu(hut.id(), false).get(0);
        OrderResponse hutOrder = service.placeOrderDetailed(
            student1, hut.id(), List.of(new CartLine(hutItem.id(), 1)), "CASH"
        );

        // Cafe Shopkeeper attempts to inspect Hut order
        SecurityException ex1 = assertThrows(SecurityException.class, () ->
            service.inspectTokenDetailed(cafeShopkeeper, hutOrder.getTokenCode())
        );
        assertTrue(ex1.getMessage().contains("belongs to another outlet"));

        // Cafe Shopkeeper attempts to redeem Hut order
        SecurityException ex2 = assertThrows(SecurityException.class, () ->
            service.redeemDetailed(cafeShopkeeper, hutOrder.getTokenCode())
        );
        assertTrue(ex2.getMessage().contains("belongs to another outlet"));
    }

    @Test
    @DisplayName("Role Access Control: Non-admin users cannot perform admin actions")
    void testNonAdminCannotMutateMenu() {
        assertDoesNotThrow(() -> service.requireRole(admin, "ADMIN"));

        SecurityException ex1 = assertThrows(SecurityException.class, () ->
            service.requireRole(student1, "ADMIN")
        );
        assertTrue(ex1.getMessage().contains("Access denied"));

        SecurityException ex2 = assertThrows(SecurityException.class, () ->
            service.requireRole(cafeShopkeeper, "ADMIN")
        );
        assertTrue(ex2.getMessage().contains("Access denied"));
    }

    @Test
    @DisplayName("Authentication Security: Invalid password returns generic rejection without data leak")
    void testInvalidPasswordRejection() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
            service.login("student@canteenx.local", "wrong-password")
        );
        assertEquals("Email or password is incorrect.", ex.getMessage());
    }
}
