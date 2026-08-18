package com.ticket.ticket_fail.repository;

import com.ticket.ticket_fail.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
