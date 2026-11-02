package com.decor.product.dto.bundles;

public record BundleProductDto(
        Long productid,
        String productName,
        int quantity
) {
}
