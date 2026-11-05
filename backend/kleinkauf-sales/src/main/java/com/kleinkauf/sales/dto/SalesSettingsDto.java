package com.kleinkauf.sales.dto;

// The store's sales settings. expirationHours null = 48
public record SalesSettingsDto(
        String alias,
        String cbu,
        String holder,
        String bank,
        Integer expirationHours
) {
}
