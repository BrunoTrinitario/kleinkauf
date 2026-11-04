package com.kleinkauf.sales.dto;

// Transfer details
public record PaymentDetailsDto(
        String alias,
        String cbu,
        String holder,
        String bank
) {
}
