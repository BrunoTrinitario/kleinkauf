package com.kleinkauf.product.dto.bundles;

import com.kleinkauf.product.entities.BundlePricingType;

import java.math.BigDecimal;

// pricingType null = FIXED (as before: price only). FIXED uses price (null = no price); PERCENT and AMOUNT, discount
public record UpdateBundlePriceDto(
        BigDecimal price,
        BundlePricingType pricingType,
        BigDecimal discount
) {
}
