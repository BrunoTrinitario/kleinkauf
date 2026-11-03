package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.DeliveryType;

import java.util.List;

// Pedido desde la tienda. address solo con ENVIO
public record NewOrderDto(
        List<NewOrderItemDto> items,
        BuyerDto buyer,
        DeliveryType deliveryType,
        AddressDto address,
        String notes
) {
}
