package com.decor.product.dto.bundles;

public record NewBundleProductDto (
        Long bundleId,
        Long productId,
        Integer quantity
){
}
