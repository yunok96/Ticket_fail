package com.ticket.ticket_fail.service;

import com.ticket.ticket_fail.entity.OptimisticLockPerformance;
import com.ticket.ticket_fail.entity.Performance;
import com.ticket.ticket_fail.entity.Reservation;
import com.ticket.ticket_fail.repository.OptimisticLockPerformanceRepository;
import com.ticket.ticket_fail.repository.PerformanceRepository;
import com.ticket.ticket_fail.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticLockReservationService {
    private final OptimisticLockPerformanceRepository performanceRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Stage 3: prevent overbooking with an optimistic lock.
     *
     * Nothing is locked. OptimisticLockPerformance carries a @Version column,
     * so Hibernate appends "and version = ?" to the update and bumps the
     * version as it writes. A caller that read a stale version updates no
     * rows and is rejected at commit time, which means conflicts are detected
     * after the fact rather than prevented. Correctness holds, but a losing
     * caller is turned away even when a seat is still free — see
     * OptimisticLockRetryReservationService for the missing half.
     */
    @Transactional
    public void reserve(long performanceId, String userId) {
        OptimisticLockPerformance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Performance not found: " + performanceId));

        performance.reserve();

        reservationRepository.save(new Reservation(performanceId, userId));
    }
}
