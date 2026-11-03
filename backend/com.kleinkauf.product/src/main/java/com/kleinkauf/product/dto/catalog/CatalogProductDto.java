package com.kleinkauf.product.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

// price null = 'consultar precio'
public record CatalogProductDto(
        // Lo usa el carrito para armar el pedido
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
