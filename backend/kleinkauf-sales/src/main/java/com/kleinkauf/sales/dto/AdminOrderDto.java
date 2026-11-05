package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Order detail for the administrator
public record AdminOrderDto(
        String number,
        OrderStatus status,
        SalesChannel channel,
        PaymentMethod paymentMethod,
        BuyerDto buyer,
        Long userId,
        DeliveryType deliveryType,
        AddressDto address,
        String notes,
        List<AdminOrderItemDto> items,
        BigDecimal discount,
        BigDecimal total,
        boolean stockReview,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<StatusChangeDto> history,
        List<ReceiptDto> receipts
) {
}
