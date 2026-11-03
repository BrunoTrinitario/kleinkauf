package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Fila de las listas de pedidos (mis pedidos y panel); units es la suma de las cantidades
public record OrderSummaryDto(
        String number,
        OrderStatus status,
        SalesChannel channel,
        PaymentMethod paymentMethod,
        String buyerName,
        String buyerEmail,
        int units,
        BigDecimal total,
        boolean stockReview,
        LocalDateTime createdAt
) {
}
