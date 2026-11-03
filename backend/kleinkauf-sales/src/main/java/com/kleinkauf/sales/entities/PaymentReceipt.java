package com.kleinkauf.sales.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
Comprobante de transferencia de un pedido. El archivo esta en disco (ReceiptStorage), una carpeta por tienda.
Puede haber varios si hubo rechazos (hasta 5). No se borran: quedan como respaldo de la venta.
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

    // Ruta relativa a receipts.path: <tenant>/<numero>/<uuid>.<ext>
    @Column(nullable = false)
    private String path;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
