package com.kleinkauf.product.dto.catalog;

import java.util.Locale;

public enum CatalogSort {
    NAME,
    PRICE_ASC,
    PRICE_DESC,
    // Best sellers first (sales counter); the number is not shown
    BEST_SELLERS;

    // name | price_asc | price_desc | best_sellers (name by default)
    public static CatalogSort parse(String value) {
        if (value == null || value.isBlank()) return NAME;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
