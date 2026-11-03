package com.kleinkauf.product.dto.bundles;

import java.math.BigDecimal;

public record UpdateBundlePriceDto(
        BigDecimal price
) {
}
