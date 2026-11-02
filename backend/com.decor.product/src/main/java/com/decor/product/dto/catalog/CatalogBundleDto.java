package com.decor.product.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

/*
Combo en el catalogo publico. listPrice es la suma de los productos (para mostrar el ahorro);
null si alguno no tiene precio. image es la primera imagen del primer producto que tenga.
 */
public record CatalogBundleDto(
        Long id,
        String name,
        BigDecimal price,
        BigDecimal listPrice,
        Availability availability,
        String image,
        List<CatalogBundleProductDto> products
) {
}
