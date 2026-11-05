package com.kleinkauf.product.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

// price null = 'consultar precio'
public record CatalogProductDto(
        // Used by the cart to build the order
        Long id,
        String slug,
        String name,
        String category,
        String description,
        BigDecimal price,
        Availability availability,
        boolean featured,
        List<String> images
) {
}
