package com.ticket.ticket_fail.repository;

import com.ticket.ticket_fail.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
}
