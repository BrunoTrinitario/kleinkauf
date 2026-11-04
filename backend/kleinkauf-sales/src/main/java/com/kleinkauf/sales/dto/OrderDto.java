package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
Order as seen by the buyer (guest lookup). No costs or internal data.
payment only while the receipt can be uploaded (PENDIENTE_PAGO or RECHAZADO).
 */
public record OrderDto(
        String number,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        BuyerDto buyer,
        DeliveryType deliveryType,
        AddressDto address,
        String notes,
        List<OrderItemDto> items,
        BigDecimal discount,
        BigDecimal total,
        PaymentDetailsDto payment,
        List<StatusChangeDto> history,
        // Uploaded receipts (the buyer cannot download them)
        int receipts
) {
}
