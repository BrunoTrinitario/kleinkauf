package com.decor.sales.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id"),
        @Index(name = "idx_orders_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Codigo aleatorio (K7QM-4X2P) para que no se pueda recorrer; unico en la tienda
    @Column(nullable = false, unique = true, length = 9)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SalesChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // Datos del comprador: obligatorios online, opcionales en el local (consumidor final)
    @Column(name = "buyer_name", length = 100)
    private String buyerName;

    // Guardado en minusculas: la consulta del invitado lo compara asi
    @Column(name = "buyer_email", length = 150)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 30)
    private String buyerPhone;

    // Usuario logueado que hizo el pedido (id del microservicio de usuarios); null si fue invitado
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 10)
    private DeliveryType deliveryType;

    @Embedded
    private Address address;

    @Column(length = 500)
    private String notes;

    @ColumnDefault("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    // Suma de los subtotales menos el descuento
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // Venta del local sin stock suficiente: products hizo un AJUSTE y hay que revisar
    @ColumnDefault("false")
    @Column(name = "stock_review", nullable = false)
    private boolean stockReview;

    // Hasta cuando se puede subir el comprobante (PENDIENTE_PAGO / RECHAZADO)
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    // Cambia el estado y lo deja en el historial
    public void changeStatus(OrderStatus newStatus, Long byUserId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        this.status = newStatus;
        this.updatedAt = now;
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(this);
        entry.setStatus(newStatus);
        entry.setUserId(byUserId);
        entry.setReason(reason);
        entry.setCreatedAt(now);
        history.add(entry);
    }
}
