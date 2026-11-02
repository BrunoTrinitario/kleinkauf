package com.decor.product.dto.catalog;

/*
Disponibilidad publica: no muestra el stock exacto.
POCAS_UNIDADES usa el stock minimo del producto (si es 0, nunca aparece).
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
