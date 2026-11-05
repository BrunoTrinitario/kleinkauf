package com.kleinkauf.product.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/*
Stock reservation for a sales order, by order number (reference).
Makes reserve and release idempotent: if sales retries, the stock does not move twice.
If the release arrives before the reservation (timeout in sales), it stays RELEASED and the reservation
that arrives later is rejected.
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation {

    public enum Status {
        RESERVED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    // Whether the order was already added to the sold counter (so it is not added or subtracted twice)
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean sold;

    // In-store sale: products that ran short of stock and were adjusted (comma separated); null if nothing was short
    @Column(length = 1000)
    private String shortages;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
