package com.kleinkauf.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record LightProductDto(
        Long id,
        String name,
        String slug,
        String category,
        String description,
        int stock,
        BigDecimal price,
        boolean featured,
        List<String> images,
        // Minimum stock: the dashboard needs it to edit it (and to warn about low stock)
        int minStock
) {
}
