package edu.canteenx;

import edu.canteenx.dto.OrderResponse;
import edu.canteenx.model.*;
import edu.canteenx.model.MenuItem;
import edu.canteenx.service.CanteenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanteenX — Concurrency & Race Condition Tests")
public class ConcurrencyProtectionTest {

    private static CanteenService service;
    private static Student student;
    private static Shopkeeper cafeShopkeeper;
    private static Canteen cafe;
    private static MenuItem testItem;

    @BeforeAll
    static void setUp() throws SQLException {
        service = new CanteenService();
        service.ensureDemoAccounts();

        student = (Student) service.login("student@canteenx.local", "student123");
        cafeShopkeeper = (Shopkeeper) service.login("cafe@canteenx.local", "cafe123");

        cafe = service.canteens().stream().filter(c -> "CAF".equals(c.tokenPrefix())).findFirst().orElseThrow();
        testItem = service.menu(cafe.id(), false).get(0);
    }

    @Test
    @DisplayName("Simultaneous multi-threaded order creation produces unique sequential token codes")
    void testConcurrentOrderCreationUniqueTokens() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        Set<String> generatedTokens = Collections.synchronizedSet(new HashSet<>());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to align
                    OrderResponse order = service.placeOrderDetailed(
                        student, cafe.id(), List.of(new CartLine(testItem.id(), 1)), "CAMPUS_UPI"
                    );
                    generatedTokens.add(order.getTokenCode());
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent order placement threads must complete within timeout");
        assertTrue(errors.isEmpty(), "No concurrent order placement should throw unexpected errors: " + errors);
        assertEquals(threadCount, generatedTokens.size(), "Every concurrently placed order must receive a globally unique token code");
    }

    @Test
    @DisplayName("Simultaneous multi-threaded redemption of the same token permits exactly one winner")
    void testConcurrentDuplicateRedemptionProtection() throws Exception {
        // First create a single order to be redeemed
        OrderResponse order = service.placeOrderDetailed(
            student, cafe.id(), List.of(new CartLine(testItem.id(), 1)), "CAMPUS_UPI"
        );
        String tokenCode = order.getTokenCode();

        int raceThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(raceThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(raceThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < raceThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize thread starts
                    service.redeemDetailed(cafeShopkeeper, tokenCode);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Unexpected redemption error: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release race condition
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All redemption threads must finish within timeout");
        assertEquals(1, successCount.get(), "Exactly ONE shopkeeper redemption request must succeed");
        assertEquals(raceThreads - 1, conflictCount.get(), "All other concurrent attempts must be safely rejected with conflict");
    }
}
