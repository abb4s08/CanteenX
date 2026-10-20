package edu.canteenx;

import edu.canteenx.model.Admin;
import edu.canteenx.model.Shopkeeper;
import edu.canteenx.model.Student;
import edu.canteenx.model.User;
import edu.canteenx.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanteenX — Unit Tests")
public class CanteenServiceUnitTest {

    @Test
    @DisplayName("Token format must follow PREFIX-0000000 format")
    void testTokenFormatting() {
        String prefix = "CAF";
        long seq1 = 1;
        long seq137 = 137;
        long seq99999 = 99999;

        assertEquals("CAF-0000001", String.format(Locale.ROOT, "%s-%07d", prefix, seq1));
        assertEquals("CAF-0000137", String.format(Locale.ROOT, "%s-%07d", prefix, seq137));
        assertEquals("HUT-0099999", String.format(Locale.ROOT, "%s-%07d", "HUT", seq99999));
    }

    @Test
    @DisplayName("BCrypt password hashing and verification")
    void testBCryptPasswordHashing() {
        String plain = "student123";
        String hash = PasswordUtil.hash(plain);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
        assertTrue(PasswordUtil.verify(plain, hash));
        assertFalse(PasswordUtil.verify("wrongpassword", hash));
        assertFalse(PasswordUtil.verify("", hash));
        assertFalse(PasswordUtil.verify(plain, null));
    }

    @Test
    @DisplayName("OOP Polymorphism: User inheritance hierarchy and role dispatch")
    void testUserRoleHierarchy() {
        User student = new Student(10L, "Aarav", "aarav@college.edu");
        User shopkeeper = new Shopkeeper(20L, "Ramesh", "ramesh@canteenx.local", 1L);
        User admin = new Admin(30L, "Campus Admin", "admin@canteenx.local");

        assertEquals("STUDENT", student.roleName());
        assertEquals("SHOPKEEPER", shopkeeper.roleName());
        assertEquals("ADMIN", admin.roleName());

        assertTrue(student instanceof Student);
        assertTrue(shopkeeper instanceof Shopkeeper);
        assertTrue(admin instanceof Admin);

        assertEquals(1L, ((Shopkeeper) shopkeeper).getCanteenId());
    }

    @Test
    @DisplayName("OOP Polymorphism: PaymentStrategy implementations process payments consistently")
    void testPaymentStrategyPolymorphism() {
        PaymentStrategy upi = new CampusUpiPaymentStrategy();
        PaymentStrategy card = new StudentSmartCardPaymentStrategy();
        PaymentStrategy cash = new CashPaymentStrategy();

        assertEquals("CAMPUS_UPI", upi.getMethodName());
        assertEquals("SMART_CARD", card.getMethodName());
        assertEquals("CASH", cash.getMethodName());

        PaymentStrategy.PaymentResult upiRes = upi.process(101L, new BigDecimal("120.00"));
        assertTrue(upiRes.success());
        assertEquals("DEMO_PAID", upiRes.status());
        assertTrue(upiRes.reference().startsWith("UPI-REC-"));

        PaymentStrategy.PaymentResult cardRes = card.process(102L, new BigDecimal("55.00"));
        assertTrue(cardRes.success());
        assertEquals("DEMO_PAID", cardRes.status());
        assertTrue(cardRes.reference().startsWith("CARD-"));

        PaymentStrategy.PaymentResult cashRes = cash.process(103L, new BigDecimal("35.00"));
        assertTrue(cashRes.success());
        assertEquals("DEMO_PAID", cashRes.status());
        assertTrue(cashRes.reference().startsWith("CASH-CTR-"));
    }

    @Test
    @DisplayName("Role-based authorization checks in CanteenService")
    void testRequireRoleAuthorization() {
        CanteenService service = new CanteenService();
        Student student = new Student(1L, "Test Student", "test@college.edu");
        Admin admin = new Admin(2L, "Test Admin", "admin@canteenx.local");

        assertDoesNotThrow(() -> service.requireRole(student, "STUDENT"));
        assertDoesNotThrow(() -> service.requireRole(admin, "ADMIN"));

        SecurityException ex1 = assertThrows(SecurityException.class, () -> service.requireRole(student, "ADMIN"));
        assertTrue(ex1.getMessage().contains("Access denied"));

        SecurityException ex2 = assertThrows(SecurityException.class, () -> service.requireRole(null, "STUDENT"));
        assertTrue(ex2.getMessage().contains("Access denied"));
    }
}
