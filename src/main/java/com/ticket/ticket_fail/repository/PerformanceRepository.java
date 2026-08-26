package com.ticket.ticket_fail.repository;

import com.ticket.ticket_fail.entity.Performance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Performance> findWithLockById(Long id);
}
