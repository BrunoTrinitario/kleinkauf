package com.decor.sales.dto;

import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
Pedido visto por el comprador (consulta del invitado). Sin costos ni datos internos.
payment solo mientras falta pagar (PENDIENTE_PAGO o RECHAZADO).
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
        List<StatusChangeDto> history
) {
}
