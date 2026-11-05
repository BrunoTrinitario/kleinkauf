package com.kleinkauf.users.dtos;

/*
permissions: PermissionArea bit mask
(PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16).
 */
public record RolePermissionDto(
        String role,
        int permissions
) {
}
