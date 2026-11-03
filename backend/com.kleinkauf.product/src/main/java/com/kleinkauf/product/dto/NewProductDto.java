package com.kleinkauf.product.dto;

import java.math.BigDecimal;

/*
stock es el stock inicial: queda registrado como movimiento de ENTRADA.
 */
public record NewProductDto(
        String name,
        String category,
        String description,
        int stock,
        int minStock,
        BigDecimal price,
        BigDecimal cost
) {
}
