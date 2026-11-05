package com.kleinkauf.product.dto;

import java.math.BigDecimal;

/*
stock is the initial stock: it is recorded as an ENTRADA movement.
 */
public record NewProductDto(
        String name,
        String category,
        String description,
        int stock,
        int minStock,
        BigDecimal price,
        BigDecimal cost
) {
}
