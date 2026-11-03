package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;

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
