package com.decor.sales.dto;

// Consulta del invitado: por POST para que el email no quede en la URL ni en los logs
public record LookupDto(
        String email,
        String number
) {
}
