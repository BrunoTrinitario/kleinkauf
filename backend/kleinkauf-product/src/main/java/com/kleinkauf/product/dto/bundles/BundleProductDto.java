package com.kleinkauf.product.dto.bundles;

import java.math.BigDecimal;

// price: the product's (null = no price). image: its first image (null if it has none)
public record BundleProductDto(
        Long productid,
        String productName,
        int quantity,
        BigDecimal price,
        String image
) {
}
