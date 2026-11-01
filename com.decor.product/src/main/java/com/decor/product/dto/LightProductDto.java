package com.decor.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record LightProductDto(
        Long id,
        String name,
        String category,
        String description,
        int stock,
        BigDecimal price,
        List<String> images
) {
}
