package com.kleinkauf.sales.dto;

// apartment is optional; the rest is required for ENVIO
public record AddressDto(
        String street,
        String number,
        String apartment,
        String city,
        String province,
        String postalCode
) {
}
