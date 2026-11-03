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
        // Stock minimo: el panel lo necesita para editarlo (y para avisar de stock bajo)
        int minStock
) {
}
