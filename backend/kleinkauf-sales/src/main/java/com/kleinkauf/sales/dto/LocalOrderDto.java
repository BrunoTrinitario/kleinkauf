package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/*
Venta del local (ya cobrada). status: PAGADO (para preparar o enviar despues) o ENTREGADO.
discount: monto en pesos sobre el subtotal (null = 0). buyer opcional (consumidor final).
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
