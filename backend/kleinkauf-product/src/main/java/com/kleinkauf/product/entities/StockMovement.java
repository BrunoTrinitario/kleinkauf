package com.kleinkauf.product.entities;

import com.kleinkauf.common.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
History of a product's stock changes. Never modified or deleted:
each change is a new movement.
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

    // Difference applied to the stock (positive or negative)
    @Column(nullable = false)
    private int quantity;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    private String reason;

    // Number of the order that reserved or returned the stock (null in manual movements)
    @Column(length = 40)
    private String reference;

    // User who recorded the movement (id from the users microservice)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
