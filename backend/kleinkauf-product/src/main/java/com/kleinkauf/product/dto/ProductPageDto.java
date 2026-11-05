package com.kleinkauf.product.dto;

import java.util.List;

// A page of the dashboard product listing (same format as the catalog, but with exact stock)
public record ProductPageDto(
        List<LightProductDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
