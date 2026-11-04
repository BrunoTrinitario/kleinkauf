package com.kleinkauf.product.dto.bundles;

import java.math.BigDecimal;
import java.util.List;

// El combo se crea completo: nombre, productos (al menos uno, sin repetir) y precio (null = no sale en el catalogo)
public record NewBundleDto(
        String name,
        Boolean enabled,
        BigDecimal price,
        List<NewBundleProductDto> products
) {
}
