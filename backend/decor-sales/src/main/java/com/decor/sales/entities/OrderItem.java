package com.decor.sales.entities;

import com.decor.common.dto.stock.ReservationItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/*
Linea del pedido. Nombre, precios y costo se copian al momento de la venta:
si despues cambia el producto, el pedido y los reportes no cambian.
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

    // Id del producto o combo en products
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    // Precio de lista al vender
    @Column(name = "list_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal listPrice;

    // Precio cobrado (en el local el administrador lo puede cambiar; online es el de lista)
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Costo al vender (para la ganancia); null si el producto no tenia costo cargado
    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
