package com.decor.product.dto.catalog;

import com.decor.product.dto.MetaDataDto;

import java.math.BigDecimal;
import java.util.List;

// Detalle para el link compartido (/catalog/products/{slug}); price null = 'consultar precio'
public record CatalogProductDetailDto(
        // Lo usa el carrito para armar el pedido
        Long id,
        String slug,
        String name,
        String category,
        String description,
        BigDecimal price,
        Availability availability,
        boolean featured,
        List<String> images,
        MetaDataDto metadata
) {
}
