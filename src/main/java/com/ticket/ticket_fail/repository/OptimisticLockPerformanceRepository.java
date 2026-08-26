package com.ticket.ticket_fail.repository;

import com.ticket.ticket_fail.entity.OptimisticLockPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimisticLockPerformanceRepository extends JpaRepository<OptimisticLockPerformance, Long> {
}
