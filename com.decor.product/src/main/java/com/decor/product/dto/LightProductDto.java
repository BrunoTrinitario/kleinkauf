package com.decor.product.dto;

public record LightProductDto(
        Long id,
        String name,
        String category,
        String description,
        int stock
) {
}
