package com.kleinkauf.sales.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Direccion de envio (solo con DeliveryType.ENVIO)
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @Column(name = "address_street", length = 100)
    private String street;

    @Column(name = "address_number", length = 100)
    private String number;

    // Piso / departamento (opcional)
    @Column(name = "address_apartment", length = 100)
    private String apartment;

    @Column(name = "address_city", length = 100)
    private String city;

    @Column(name = "address_province", length = 100)
    private String province;

    @Column(name = "address_postal_code", length = 100)
    private String postalCode;
}
