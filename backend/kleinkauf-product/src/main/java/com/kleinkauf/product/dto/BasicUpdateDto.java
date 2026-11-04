package com.kleinkauf.product.dto;

/*
El stock no se modifica aca: se usan los movimientos de stock (/api/v1/products/{id}/stock/movements).
Los campos en null no cambian: se puede mandar solo lo que se edita.
 */
public record BasicUpdateDto(
        String name,
        String category,
        String description,
        Integer minStock
) {
}
