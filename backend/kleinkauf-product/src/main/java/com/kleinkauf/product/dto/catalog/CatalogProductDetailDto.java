package com.kleinkauf.product.dto.catalog;

import com.kleinkauf.product.dto.MetaDataDto;

import java.math.BigDecimal;
import java.util.List;

// Detail for the shared link (/catalog/products/{slug}); price null = 'consultar precio'
public record CatalogProductDetailDto(
        // Used by the cart to build the order
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
