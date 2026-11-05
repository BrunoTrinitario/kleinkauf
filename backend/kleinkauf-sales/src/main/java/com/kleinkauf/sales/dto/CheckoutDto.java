package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;

import java.util.List;

/*
What the store needs to show the checkout. paymentConfigured false: the store has not
entered the transfer details yet and orders cannot be created.
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
