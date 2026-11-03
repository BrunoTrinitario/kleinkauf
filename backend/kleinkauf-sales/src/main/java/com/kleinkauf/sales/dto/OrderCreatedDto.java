package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedDto(
        String number,
        OrderStatus status,
        BigDecimal total,
        LocalDateTime expiresAt,
        PaymentDetailsDto payment
) {
}
