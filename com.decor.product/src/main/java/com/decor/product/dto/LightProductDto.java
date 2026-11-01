package com.decor.product.dto;

import java.util.List;

public record LightProductDto(
        Long id,
        String name,
        String category,
        String description,
        int stock,
        List<String> images
) {
}
