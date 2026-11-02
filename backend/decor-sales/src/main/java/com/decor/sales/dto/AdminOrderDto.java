package com.decor.sales.dto;

import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Detalle del pedido para el administrador
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
