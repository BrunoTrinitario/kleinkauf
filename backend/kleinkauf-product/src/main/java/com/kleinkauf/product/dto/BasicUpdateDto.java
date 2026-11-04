package com.kleinkauf.product.dto;

/*
Stock is not modified here: stock movements are used instead (/api/v1/products/{id}/stock/movements).
Null fields do not change: only what is being edited can be sent.
 */
public record BasicUpdateDto(
        String name,
        String category,
        String description,
        Integer minStock
) {
}
