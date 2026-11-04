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

    // Random code (K7QM-4X2P) so orders cannot be enumerated; unique within the store
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

    // Buyer data: required online, optional in the store (final consumer)
    @Column(name = "buyer_name", length = 100)
    private String buyerName;

    // Stored in lowercase: the guest lookup compares it that way
    @Column(name = "buyer_email", length = 150)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 30)
    private String buyerPhone;

    // Logged-in user who placed the order (id from the users microservice); null if it was a guest
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

    // Sum of the subtotals minus the discount
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // In-store sale without enough stock: products made an AJUSTE and it needs to be reviewed
    @ColumnDefault("false")
    @Column(name = "stock_review", nullable = false)
    private boolean stockReview;

    // Until when the receipt can be uploaded (PENDIENTE_PAGO / RECHAZADO)
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // When the payment was validated (or the in-store sale was entered): the sale date in the reports
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Optimistic locking: an administrator change, a receipt and the expiry do not overwrite each other
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

    // Sum of the subtotals before the discount
    public BigDecimal subtotal() {
        return items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Changes the status and records it in the history
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
