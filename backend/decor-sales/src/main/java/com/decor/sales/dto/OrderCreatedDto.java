package com.decor.sales.dto;

import com.decor.sales.entities.OrderStatus;

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
