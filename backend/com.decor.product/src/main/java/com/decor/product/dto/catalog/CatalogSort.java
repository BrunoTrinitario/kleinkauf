package com.decor.product.dto.catalog;

import java.util.Locale;

public enum CatalogSort {
    NAME,
    PRICE_ASC,
    PRICE_DESC,
    // Mas vendidos primero (contador de sales); no se muestra el numero
    BEST_SELLERS;

    // name | price_asc | price_desc | best_sellers (por defecto name)
    public static CatalogSort parse(String value) {
        if (value == null || value.isBlank()) return NAME;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
