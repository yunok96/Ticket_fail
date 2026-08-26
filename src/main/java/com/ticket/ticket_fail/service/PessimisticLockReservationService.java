package com.ticket.ticket_fail.service;

import com.ticket.ticket_fail.entity.Performance;
import com.ticket.ticket_fail.entity.Reservation;
import com.ticket.ticket_fail.repository.PerformanceRepository;
import com.ticket.ticket_fail.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLockReservationService {
    private final PerformanceRepository performanceRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Stage 2: prevent overbooking with pessimistic lock.
     *
     * The read of reservedSeats and the write that follows are not atomic,
     * so two concurrent callers can both observe the same value, both pass
     * the sold-out check, and both increment. This is a lost update, and it
     * is the exact failure this stage is meant to reproduce.
     */
    @Transactional
    public void reserve(long performanceId, String userId) {
        Performance performance = performanceRepository.findWithLockById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Performance not found: " + performanceId));

        performance.reserve();

        reservationRepository.save(new Reservation(performanceId, userId));
    }
}
