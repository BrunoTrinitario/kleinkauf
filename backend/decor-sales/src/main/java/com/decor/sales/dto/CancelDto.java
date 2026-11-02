package com.decor.sales.dto;

// Cancelacion del comprador: el email del pedido identifica al invitado
public record CancelDto(
        String email
) {
}
