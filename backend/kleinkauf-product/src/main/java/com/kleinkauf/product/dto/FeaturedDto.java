package com.kleinkauf.product.dto;

/*
featured: si el producto se muestra como destacado.
order: posicion entre los destacados (opcional, 0 o mas). Se ignora si featured es false.
 */
public record FeaturedDto(
        boolean featured,
        Integer order
) {
}
