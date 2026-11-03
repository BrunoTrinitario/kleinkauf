package com.kleinkauf.users.dtos;

/*
permissions: mascara de bits de PermissionArea
(PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16).
 */
public record RolePermissionDto(
        String role,
        int permissions
) {
}
