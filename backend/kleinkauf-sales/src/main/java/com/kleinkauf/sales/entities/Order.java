package com.kleinkauf.sales.entities;

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
        @Index(name = "idx_orders_created", columnList = "created_at"),
        @Index(name = "idx_orders_expiration", columnList = "status, expires_at"),
        @Index(name = "idx_orders_paid", columnList = "paid_at")
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

    // Cuando se valido el pago (o se cargo la venta del local): la fecha de la venta en los reportes
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Control optimista: un cambio del administrador, un comprobante y el vencimiento no se pisan
    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentReceipt> receipts = new ArrayList<>();

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

    public void addReceipt(PaymentReceipt receipt) {
        receipt.setOrder(this);
        receipts.add(receipt);
    }

    // Suma de los subtotales antes del descuento
    public BigDecimal subtotal() {
        return items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Cambia el estado y lo deja en el historial
    public void changeStatus(OrderStatus newStatus, Long byUserId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        this.status = newStatus;
        this.updatedAt = now;
        if (newStatus == OrderStatus.PAGADO) this.paidAt = now;
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(this);
        entry.setStatus(newStatus);
        entry.setUserId(byUserId);
        entry.setReason(reason);
        entry.setCreatedAt(now);
        history.add(entry);
    }
}
