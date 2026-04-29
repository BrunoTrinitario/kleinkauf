package com.decor.product.dto;

public record NewProductDto(
        String name,
        String category,
        String description,
        int stock
) {
}
