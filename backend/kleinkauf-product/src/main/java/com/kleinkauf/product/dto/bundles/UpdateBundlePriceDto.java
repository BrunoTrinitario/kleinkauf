package com.kleinkauf.product.dto.bundles;

import com.kleinkauf.product.entities.BundlePricingType;

import java.math.BigDecimal;

// pricingType null = FIXED (como antes: solo price). FIXED usa price (null = sin precio); PERCENT y AMOUNT, discount
public record UpdateBundlePriceDto(
        BigDecimal price,
        BundlePricingType pricingType,
        BigDecimal discount
) {
}
