package com.kleinkauf.sales.entities;

import com.kleinkauf.common.dto.stock.ReservationItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/*
Order line. Name, prices and cost are copied at sale time:
if the product changes later, the order and the reports do not change.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReservationItemType type;

    // Id of the product or bundle in products
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    // List price at sale time; null if it was 'a consultar' (in-store sales only)
    @Column(name = "list_price", precision = 12, scale = 2)
    private BigDecimal listPrice;

    // Price charged (in the store the administrator can change it; online it is the list price)
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Cost at sale time (for the profit); null if the product had no cost set
    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
