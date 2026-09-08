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
public class ReservationExecutor {
    private final PerformanceRepository performanceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void reserve(long performanceId, String userId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Performance not found: " + performanceId));

        performance.reserve();

        reservationRepository.save(new Reservation(performanceId, userId));
    }
}
