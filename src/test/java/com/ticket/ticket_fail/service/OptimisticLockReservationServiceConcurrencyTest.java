package com.ticket.ticket_fail.service;

import com.ticket.ticket_fail.entity.OptimisticLockPerformance;
import com.ticket.ticket_fail.repository.OptimisticLockPerformanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OptimisticLockReservationServiceConcurrencyTest {

    private static final int TOTAL_SEATS = 10;
    private static final int CONCURRENT_REQUESTS = 10;
    private static final int THREAD_POOL_SIZE = CONCURRENT_REQUESTS;
    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private OptimisticLockReservationService reservationService;

    @Autowired
    private OptimisticLockPerformanceRepository optimisticLockPerformanceRepository;

    @Test
    @DisplayName("optimistic locking never overbooks, but rejects callers that lose the race")
    void concurrentReservationsFailFastOnVersionConflict() throws InterruptedException {
        OptimisticLockPerformance saved =
                optimisticLockPerformanceRepository.save(new OptimisticLockPerformance("Hamlet", TOTAL_SEATS));
        long performanceId = saved.getId();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
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
                } catch (ObjectOptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();         // lost the race, seat may still be free
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

        OptimisticLockPerformance result =
                optimisticLockPerformanceRepository.findById(performanceId).orElseThrow();

        System.out.println("=== Stage 3: Optimistic lock, no retry ===");
        System.out.println("total seats    : " + TOTAL_SEATS);
        System.out.println("requests       : " + CONCURRENT_REQUESTS);
        System.out.println("succeeded      : " + successCount.get());
        System.out.println("sold out       : " + soldOutCount.get());
        System.out.println("conflicts      : " + conflictCount.get());
        System.out.println("unexpected     : " + unexpectedCount.get());
        System.out.println("reserved seats : " + result.getReservedSeats());

        assertThat(finished)
                .as("all %d requests should finish within %ds", CONCURRENT_REQUESTS, TIMEOUT_SECONDS)
                .isTrue();

        assertThat(unexpectedCount.get())
                .as("no request should fail for reasons other than sold out or version conflict")
                .isZero();

        assertThat(result.getReservedSeats())
                .as("the counter must match the number of successful reservations")
                .isEqualTo(successCount.get());

        assertThat(result.getReservedSeats())
                .as("optimistic locking must never overbook")
                .isLessThanOrEqualTo(TOTAL_SEATS);

        assertThat(conflictCount.get())
                .as("without retry, callers that lose the version race are simply rejected")
                .isPositive();
    }
}