package com.kleinkauf.sales.dto;

import java.time.LocalDateTime;

// Receipt for the administrator; downloaded at /admin/orders/{number}/receipts/{id}
public record ReceiptDto(
        Long id,
        String contentType,
        long size,
        LocalDateTime createdAt
) {
}
