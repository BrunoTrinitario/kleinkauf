package com.decor.sales.dto;

import com.decor.sales.entities.DeliveryType;

import java.util.List;

/*
Lo que necesita la tienda para mostrar el checkout. paymentConfigured false: la tienda todavia
no cargo los datos para transferir y no se pueden crear pedidos.
 */
public record CheckoutDto(
        boolean paymentConfigured,
        PaymentDetailsDto payment,
        int expirationHours,
        List<DeliveryType> deliveryTypes,
        int maxLines,
        int maxQuantity
) {
}
