package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
Pedido visto por el comprador (consulta del invitado). Sin costos ni datos internos.
payment solo mientras se puede subir el comprobante (PENDIENTE_PAGO o RECHAZADO).
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
        // Comprobantes subidos (el comprador no los puede descargar)
        int receipts
) {
}
