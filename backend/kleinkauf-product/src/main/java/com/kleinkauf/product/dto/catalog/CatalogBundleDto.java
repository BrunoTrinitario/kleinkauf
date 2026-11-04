package com.kleinkauf.product.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

/*
Bundle in the public catalog. listPrice is the sum of the products (to show the savings);
null if any of them has no price. image is the first image of the first product that has one.
 */
public record CatalogBundleDto(
        Long id,
        String name,
        BigDecimal price,
        BigDecimal listPrice,
        Availability availability,
        String image,
        List<CatalogBundleProductDto> products
) {
}
