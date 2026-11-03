package com.kleinkauf.sales.dto;

import java.time.LocalDateTime;

// Comprobante para el administrador; se descarga en /admin/orders/{number}/receipts/{id}
public record ReceiptDto(
        Long id,
        String contentType,
        long size,
        LocalDateTime createdAt
) {
}
