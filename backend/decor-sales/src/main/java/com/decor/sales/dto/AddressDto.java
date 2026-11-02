package com.decor.sales.dto;

// apartment es opcional; el resto es obligatorio para ENVIO
public record AddressDto(
        String street,
        String number,
        String apartment,
        String city,
        String province,
        String postalCode
) {
}
