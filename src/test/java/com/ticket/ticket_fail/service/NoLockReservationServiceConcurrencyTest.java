package com.ticket.ticket_fail.service;

import com.ticket.ticket_fail.entity.Performance;
import com.ticket.ticket_fail.repository.PerformanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NoLockReservationServiceConcurrencyTest {

    private static final int TOTAL_SEATS = 10;
    private static final int CONCURRENT_REQUESTS = 100;

    /**
     * Must be at least CONCURRENT_REQUESTS. Every task parks on startLatch
     * while holding its worker thread, so a smaller pool would leave the
     * remaining tasks queued forever and deadlock the test.
     */
    private static final int THREAD_POOL_SIZE = CONCURRENT_REQUESTS;

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private NoLockReservationService reservationService;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("100 concurrent requests must never reserve more than the total seats")
    void concurrentReservationsMustNotOverbook() throws InterruptedException {
        Performance saved = performanceRepository.save(new Performance("Hamlet", TOTAL_SEATS));
        long performanceId = saved.getId();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();          // release every thread at the same moment
                    reservationService.reserve(performanceId, userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    soldOutCount.incrementAndGet();          // legitimate rejection
                } catch (Exception e) {
                    unexpectedCount.incrementAndGet();       // infrastructure or wiring problem
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();                      // wait until every thread is at the line
        startLatch.countDown();                  // fire

        boolean finished = doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdownNow();

        Performance result = performanceRepository.findById(performanceId).orElseThrow();

        System.out.println("=== Stage 1: no locking ===");
        System.out.println("total seats    : " + TOTAL_SEATS);
        System.out.println("requests       : " + CONCURRENT_REQUESTS);
        System.out.println("succeeded      : " + successCount.get());
        System.out.println("sold out       : " + soldOutCount.get());
        System.out.println("unexpected     : " + unexpectedCount.get());
        System.out.println("reserved seats : " + result.getReservedSeats());

        assertThat(finished)
                .as("all %d requests should finish within %ds", CONCURRENT_REQUESTS, TIMEOUT_SECONDS)
                .isTrue();

        assertThat(unexpectedCount.get())
                .as("no request should fail for reasons other than being sold out")
                .isZero();

        assertThat(result.getReservedSeats())
                .as("reserved seats must never exceed the total")
                .isEqualTo(TOTAL_SEATS);
    }
}