package com.kleinkauf.product.dto;

import java.util.List;

// Pagina del listado de productos del panel (mismo formato que el catalogo, pero con stock exacto)
public record ProductPageDto(
        List<LightProductDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
