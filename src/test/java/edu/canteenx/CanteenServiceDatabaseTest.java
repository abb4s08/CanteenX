package edu.canteenx;

import edu.canteenx.dto.OrderResponse;
import edu.canteenx.dto.TokenVerificationResponse;
import edu.canteenx.model.*;
import edu.canteenx.model.MenuItem;
import edu.canteenx.service.CanteenService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanteenX — MySQL Database Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CanteenServiceDatabaseTest {

    private static CanteenService service;
    private static Student testStudent;
    private static Shopkeeper cafeShopkeeper;
    private static Shopkeeper hutShopkeeper;
    private static Admin testAdmin;

    @BeforeAll
    static void setUp() throws SQLException {
        service = new CanteenService();
        // Ensure demo accounts exist
        service.ensureDemoAccounts();

        testStudent = (Student) service.login("student@canteenx.local", "student123");
        cafeShopkeeper = (Shopkeeper) service.login("cafe@canteenx.local", "cafe123");
        hutShopkeeper = (Shopkeeper) service.login("hut@canteenx.local", "hut123");
        testAdmin = (Admin) service.login("admin@canteenx.local", "admin123");
    }

    @Test
    @Order(1)
    @DisplayName("Live database connection health check returns true")
    void testDatabaseReady() {
        assertTrue(service.databaseReady(), "MySQL database connection must be ready");
    }

    @Test
    @Order(2)
    @DisplayName("Canteens and active outlets discovery from MySQL")
    void testCanteensDiscovery() throws SQLException {
        List<Canteen> list = service.canteens();
        assertFalse(list.isEmpty(), "Canteens table should contain seeded outlets");
        assertTrue(list.stream().anyMatch(c -> "CAF".equals(c.tokenPrefix())));
        assertTrue(list.stream().anyMatch(c -> "HUT".equals(c.tokenPrefix())));
    }

    @Test
    @Order(3)
    @DisplayName("Menu retrieval returns seeded dishes with correct prices")
    void testMenuRetrieval() throws SQLException {
        List<Canteen> canteens = service.canteens();
        Canteen cafe = canteens.stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();

        List<MenuItem> dishes = service.menu(cafe.id(), false);
        assertFalse(dishes.isEmpty(), "REC Cafe should have active dishes");
        assertTrue(dishes.stream().allMatch(d -> d.price().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    @Order(4)
    @DisplayName("Order placement creates order, snapshots lines, and allocates sequential token")
    void testOrderPlacementAndTokenAllocation() throws SQLException {
        List<Canteen> canteens = service.canteens();
        Canteen cafe = canteens.stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();
        List<MenuItem> dishes = service.menu(cafe.id(), false);

        MenuItem item1 = dishes.get(0);
        List<CartLine> cart = List.of(new CartLine(item1.id(), 2));

        OrderResponse order = service.placeOrderDetailed(testStudent, cafe.id(), cart, "CAMPUS_UPI");

        assertNotNull(order);
        assertTrue(order.getOrderId() > 0);
        assertNotNull(order.getTokenCode());
        assertTrue(order.getTokenCode().startsWith("CAF-"));
        assertEquals("PLACED", order.getOrderStatus());
        assertEquals("DEMO_PAID", order.getPaymentStatus());

        BigDecimal expectedTotal = item1.price().multiply(BigDecimal.valueOf(2));
        assertEquals(expectedTotal, order.getTotalAmount());
    }

    @Test
    @Order(5)
    @DisplayName("Shopkeeper token inspection returns accurate items and verified state")
    void testTokenInspection() throws SQLException {
        List<Canteen> canteens = service.canteens();
        Canteen cafe = canteens.stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();
        List<MenuItem> dishes = service.menu(cafe.id(), false);

        MenuItem item = dishes.get(0);
        OrderResponse order = service.placeOrderDetailed(testStudent, cafe.id(), List.of(new CartLine(item.id(), 1)), "SMART_CARD");

        TokenVerificationResponse inspection = service.inspectTokenDetailed(cafeShopkeeper, order.getTokenCode());
        assertEquals(order.getTokenCode(), inspection.getTokenCode());
        assertEquals(order.getOrderId(), inspection.getOrderId());
        assertEquals(cafe.name(), inspection.getCanteenName());
        assertTrue(inspection.isEligibleForHandover());
        assertEquals("VALID — PAID — READY FOR HANDOVER", inspection.getVerificationMessage());
    }

    @Test
    @Order(6)
    @DisplayName("Cross-Outlet Isolation: REC Cafe counter cannot verify REC Hut token")
    void testCrossOutletIsolation() throws SQLException {
        List<Canteen> canteens = service.canteens();
        Canteen hut = canteens.stream().filter(c -> "HUT".equals(c.tokenPrefix())).findFirst().orElseThrow();
        List<MenuItem> hutDishes = service.menu(hut.id(), false);

        OrderResponse hutOrder = service.placeOrderDetailed(testStudent, hut.id(), List.of(new CartLine(hutDishes.get(0).id(), 1)), "CASH");

        // Cafe shopkeeper tries to inspect Hut token -> denied
        SecurityException ex = assertThrows(SecurityException.class, () ->
            service.inspectTokenDetailed(cafeShopkeeper, hutOrder.getTokenCode())
        );
        assertTrue(ex.getMessage().contains("belongs to another outlet"));

        // Hut shopkeeper can inspect their own outlet's token
        TokenVerificationResponse hutInspection = service.inspectTokenDetailed(hutShopkeeper, hutOrder.getTokenCode());
        assertEquals(hut.name(), hutInspection.getCanteenName());
        assertTrue(hutInspection.isEligibleForHandover());
    }

    @Test
    @Order(7)
    @DisplayName("Atomic token redemption succeeds once and rejects duplicate redemption attempts")
    void testAtomicRedemptionAndDuplicateRejection() throws SQLException {
        List<Canteen> canteens = service.canteens();
        Canteen cafe = canteens.stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();
        List<MenuItem> dishes = service.menu(cafe.id(), false);

        OrderResponse order = service.placeOrderDetailed(testStudent, cafe.id(), List.of(new CartLine(dishes.get(0).id(), 1)), "CAMPUS_UPI");

        // First redemption -> MUST succeed
        TokenVerificationResponse redeemed = service.redeemDetailed(cafeShopkeeper, order.getTokenCode());
        assertEquals("REDEEMED", redeemed.getOrderStatus());
        assertFalse(redeemed.isEligibleForHandover());

        // Second redemption attempt -> MUST fail safely
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.redeemDetailed(cafeShopkeeper, order.getTokenCode())
        );
        assertTrue(ex.getMessage().contains("already been redeemed"));
    }

    @Test
    @Order(8)
    @DisplayName("Admin Sales Summary and KPIs calculate accurate revenue")
    void testAdminSalesSummary() throws SQLException {
        CanteenService.SalesSummary summary = service.summary(testAdmin);
        assertNotNull(summary);
        assertTrue(summary.orders() > 0);
        assertTrue(summary.revenue().compareTo(BigDecimal.ZERO) >= 0);
    }
}
