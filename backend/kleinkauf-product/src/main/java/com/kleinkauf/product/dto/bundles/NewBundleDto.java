package com.kleinkauf.product.dto.bundles;

import com.kleinkauf.product.entities.BundlePricingType;

import java.math.BigDecimal;
import java.util.List;

// El combo se crea completo: nombre, productos (al menos uno, sin repetir) y precio.
// pricingType null = FIXED: price (null = no sale en el catalogo). PERCENT y AMOUNT: discount sobre la suma
public record NewBundleDto(
        String name,
        Boolean enabled,
        BigDecimal price,
        List<NewBundleProductDto> products,
        BundlePricingType pricingType,
        BigDecimal discount
) {
    // Combo de precio fijo
    public NewBundleDto(String name, Boolean enabled, BigDecimal price, List<NewBundleProductDto> products) {
        this(name, enabled, price, products, null, null);
    }
}
