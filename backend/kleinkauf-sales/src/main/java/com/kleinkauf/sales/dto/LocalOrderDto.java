package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/*
In-store sale (already charged). status: PAGADO (to prepare or ship later) or ENTREGADO.
discount: amount in pesos over the subtotal (null = 0). buyer is optional (final consumer).
deliveryType null = RETIRO.
 */
public record LocalOrderDto(
        List<LocalOrderItemDto> items,
        BigDecimal discount,
        PaymentMethod paymentMethod,
        OrderStatus status,
        BuyerDto buyer,
        DeliveryType deliveryType,
        AddressDto address,
        String notes
) {
}
