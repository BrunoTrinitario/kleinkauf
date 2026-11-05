package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Row of the order lists (my orders and dashboard); units is the sum of the quantities
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
