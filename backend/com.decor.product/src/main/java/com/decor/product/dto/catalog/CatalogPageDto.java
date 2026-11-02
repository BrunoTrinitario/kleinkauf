package com.decor.product.dto.catalog;

import java.util.List;

public record CatalogPageDto(
        List<CatalogProductDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
