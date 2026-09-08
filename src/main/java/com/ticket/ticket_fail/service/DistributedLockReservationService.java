package com.ticket.ticket_fail.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DistributedLockReservationService {

    private static final String LOCK_KEY_PREFIX = "seat:lock:";
    private static final long WAIT_TIME_SECONDS = 10;

    private final RedissonClient redissonClient;
    private final ReservationExecutor executor;

    /**
     * Stage 4: prevent overbooking with a Redis distributed lock.
     *
     * The lock is acquired outside the transaction and released only after
     * ReservationExecutor.reserve has returned and its transaction has
     * committed. Taking and releasing the lock inside a @Transactional method
     * would free it before the commit, letting the next caller read a stale
     * seat count and overbook despite the lock. Unlike the database locks in
     * stages 2 and 3, this lock lives outside the database and therefore also
     * holds across multiple application instances.
     *
     * @return true if the reservation was made, false if the lock could not
     *         be acquired within the wait time. A sold-out performance still
     *         raises IllegalStateException, exactly as in the other stages.
     */
    public boolean reserve(long performanceId, String userId) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + performanceId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                return false;
            }
            executor.reserve(performanceId, userId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}