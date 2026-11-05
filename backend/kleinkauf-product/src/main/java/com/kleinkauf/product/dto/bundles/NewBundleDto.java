package com.kleinkauf.product.dto.bundles;

import com.kleinkauf.product.entities.BundlePricingType;

import java.math.BigDecimal;
import java.util.List;

// The bundle is created complete: name, products (at least one, no repeats) and price.
// pricingType null = FIXED: price (null = not shown in the catalog). PERCENT and AMOUNT: discount over the sum
public record NewBundleDto(
        String name,
        Boolean enabled,
        BigDecimal price,
        List<NewBundleProductDto> products,
        BundlePricingType pricingType,
        BigDecimal discount
) {
    // Fixed-price bundle
    public NewBundleDto(String name, Boolean enabled, BigDecimal price, List<NewBundleProductDto> products) {
        this(name, enabled, price, products, null, null);
    }
}
