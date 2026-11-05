package com.kleinkauf.product.dto.catalog;

import java.math.BigDecimal;

// Product inside a catalog bundle: just enough to show the bundle detail.
// price: the standalone product's (null = price on request). image: its first image (null if it has none)
public record CatalogBundleProductDto(
        String slug,
        String name,
        int quantity,
        String category,
        String description,
        BigDecimal price,
        String image
) {
}
