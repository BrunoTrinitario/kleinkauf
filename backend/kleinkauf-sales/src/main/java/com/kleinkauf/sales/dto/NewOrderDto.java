package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;

import java.util.List;

// Order from the store. address only with ENVIO
public record NewOrderDto(
        List<NewOrderItemDto> items,
        BuyerDto buyer,
        DeliveryType deliveryType,
        AddressDto address,
        String notes
) {
}
