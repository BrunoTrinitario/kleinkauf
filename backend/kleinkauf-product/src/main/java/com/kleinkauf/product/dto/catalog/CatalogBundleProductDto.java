package com.kleinkauf.product.dto.catalog;

import java.math.BigDecimal;

// Producto dentro de un combo del catalogo: lo justo para mostrar el detalle del combo.
// price: el del producto suelto (null = consultar). image: su primera imagen (null si no tiene)
public record CatalogBundleProductDto(
        String slug,
        String name,
        int quantity,
        String category,
        String description,
        BigDecimal price,
        String image
) {
}
