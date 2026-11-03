package com.kleinkauf.sales.dto;

// Datos para transferir
public record PaymentDetailsDto(
        String alias,
        String cbu,
        String holder,
        String bank
) {
}
