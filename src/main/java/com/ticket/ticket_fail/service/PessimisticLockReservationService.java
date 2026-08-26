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
     * Stage 2: prevent overbooking with a pessimistic lock.
     *
     * findWithLockById issues SELECT ... FOR UPDATE, so the row is locked when
     * it is read rather than when it is written, and stays locked until the
     * transaction commits. This closes the check-then-act gap that stage 1
     * exposes: a second caller cannot read the counter until the first has
     * finished writing it. The cost is throughput, since requests for the same
     * performance are now serialised.
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
