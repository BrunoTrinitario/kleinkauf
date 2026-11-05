package com.kleinkauf.sales.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
Transfer receipt of an order. The file is on disk (ReceiptStorage), one folder per store.
There can be several if there were rejections (up to 5). They are not deleted: they stay as a record of the sale.
 */
@Entity
@Table(name = "payment_receipts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Path relative to receipts.path: <tenant>/<number>/<uuid>.<ext>
    @Column(nullable = false)
    private String path;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
