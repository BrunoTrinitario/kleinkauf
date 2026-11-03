package com.kleinkauf.sales.dto;

// Configuracion de ventas de la tienda. expirationHours null = 48
public record SalesSettingsDto(
        String alias,
        String cbu,
        String holder,
        String bank,
        Integer expirationHours
) {
}
