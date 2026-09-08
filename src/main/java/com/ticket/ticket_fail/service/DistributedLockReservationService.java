package com.ticket.ticket_fail.service;

import com.ticket.ticket_fail.entity.Performance;
import com.ticket.ticket_fail.entity.Reservation;
import com.ticket.ticket_fail.repository.PerformanceRepository;
import com.ticket.ticket_fail.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DistributedLockReservationService {

    private final RedissonClient redissonClient;
    private final ReservationExecutor executor;

    /**
     * Stage 4: prevent overbooking with a Redis distributed lock.
     *
     * The lock is acquired outside the transaction and released after it
     * commits. If the lock were taken and released inside a @Transactional
     * method, it would be freed before the commit, letting the next caller
     * read a stale seat count and overbook despite the lock. Unlike the
     * database locks in stages 2 and 3, this one lives outside the database
     * and therefore also works across multiple application instances.
     */
    public boolean reserve(Long performanceId) {
        RLock lock = redissonClient.getLock("seat:lock:" + performanceId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, 3, TimeUnit.SECONDS);
            if (!acquired) {
                return false;
            }
            return executor.reserve(performanceId);   // 여기 안쪽이 @Transactional
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
