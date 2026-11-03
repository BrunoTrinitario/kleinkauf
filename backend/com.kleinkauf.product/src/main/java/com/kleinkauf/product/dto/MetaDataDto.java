package com.kleinkauf.product.dto;

public record MetaDataDto(
        String material,
        String color,
        String style,
        double height,
        double width,
        double length,
        double weight
) {
}
