package com.kleinkauf.sales.dto;

import java.util.List;

// 409 response when creating an order with old prices
public record PricesChangedDto(
        String message,
        List<PriceChangeDto> items
) {
}
