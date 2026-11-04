package com.kleinkauf.users.dtos;

// Reporte desde el footer de la tienda. email es opcional (con la sesion abierta se usa el de la cuenta)
public record NewBugReportDto(
        String description,
        String email,
        String pageUrl
) {
}
