package com.ticket.ticket_fail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Reservation {
    @Id
    @GeneratedValue
    private Long id;
    private long performanceId;
    private String userId;
    private LocalDateTime reservedTime;

    protected Reservation() {}

    public Reservation(Long performanceId, String userId) {
        this.performanceId = performanceId;
        this.userId = userId;
        this.reservedTime = LocalDateTime.now();
    }
}
