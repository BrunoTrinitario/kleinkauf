package com.kleinkauf.product.dto.catalog;

/*
Public availability: does not show the exact stock.
POCAS_UNIDADES uses the product's minimum stock (if it is 0, it never shows up).
 */
public enum Availability {
    DISPONIBLE,
    POCAS_UNIDADES,
    AGOTADO;

    public static Availability of(int stock, int minStock) {
        if (stock <= 0) return AGOTADO;
        if (minStock > 0 && stock <= minStock) return POCAS_UNIDADES;
        return DISPONIBLE;
    }
}
