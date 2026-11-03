package com.kleinkauf.product.dto;

import java.math.BigDecimal;

public record UpdatePricingDto(
        BigDecimal price,
        BigDecimal cost
) {
}
