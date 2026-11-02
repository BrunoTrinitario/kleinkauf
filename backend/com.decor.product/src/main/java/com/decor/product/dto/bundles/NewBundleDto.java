package com.decor.product.dto.bundles;

import java.math.BigDecimal;

public record NewBundleDto(
        String name,
        Boolean enabled,
        BigDecimal price
) {
}
