package com.decor.product.entities;

import com.decor.common.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
Historial de cambios de stock de un producto. No se modifica ni se borra:
cada cambio es un movimiento nuevo.
 */
@Entity
@Audited
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    // Diferencia aplicada al stock (positiva o negativa)
    @Column(nullable = false)
    private int quantity;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    private String reason;

    // Numero del pedido que reservo o devolvio el stock (null en los movimientos manuales)
    @Column(length = 40)
    private String reference;

    // Usuario que registro el movimiento (id del microservicio de usuarios)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
