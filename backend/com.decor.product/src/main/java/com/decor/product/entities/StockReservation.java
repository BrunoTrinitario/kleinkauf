package com.decor.product.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
Reserva de stock de un pedido de sales, por numero de pedido (reference).
Hace idempotentes a reservar y devolver: si sales reintenta, el stock no se mueve dos veces.
Si llega la devolucion antes que la reserva (timeout en sales), queda RELEASED y la reserva
que llegue despues se rechaza.
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
