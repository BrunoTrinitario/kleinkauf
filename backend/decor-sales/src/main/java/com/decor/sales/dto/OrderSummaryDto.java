package com.decor.sales.dto;

import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;

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
