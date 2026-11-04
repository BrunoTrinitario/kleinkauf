package com.kleinkauf.product.dto.bundles;

import java.math.BigDecimal;

// price: el del producto (null = sin precio). image: su primera imagen (null si no tiene)
public record BundleProductDto(
        Long productid,
        String productName,
        int quantity,
        BigDecimal price,
        String image
) {
}
