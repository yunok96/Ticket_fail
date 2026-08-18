package com.ticket.ticket_fail.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Performance {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int totalSeats;
    private int reservedSeats;

    protected Performance() {}

    public Performance(String title, int totalSeats) {
        this.title = title;
        this.totalSeats = totalSeats;
        this.reservedSeats = 0;
    }

    public void reserve() {
        if (reservedSeats >= totalSeats) {
            throw new IllegalStateException("Sold out");
        }
        reservedSeats++;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getReservedSeats() {
        return reservedSeats;
    }
}
