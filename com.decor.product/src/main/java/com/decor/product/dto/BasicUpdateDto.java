package com.decor.product.dto;

public record BasicUpdateDto(
        String name,
        String category,
        String description,
        int stock
) {
}
