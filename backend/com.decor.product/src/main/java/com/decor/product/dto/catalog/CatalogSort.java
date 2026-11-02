package com.decor.product.dto.catalog;

import java.util.Locale;

public enum CatalogSort {
    NAME,
    PRICE_ASC,
    PRICE_DESC;

    // name | price_asc | price_desc (por defecto name)
    public static CatalogSort parse(String value) {
        if (value == null || value.isBlank()) return NAME;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
