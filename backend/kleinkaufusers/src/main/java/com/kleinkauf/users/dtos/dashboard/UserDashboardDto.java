package com.kleinkauf.users.dtos.dashboard;

import com.kleinkauf.users.dtos.platform.RegistrationsDto;

import java.util.Map;

/*
GET /api/v1/users/dashboard: panel de la tienda (solo ADMINISTRADOR, docs/panel-admin.md).
- active: entraron en los ultimos activeWindowDays dias (lastLogin).
- byRole: ADMINISTRADOR, EMPLEADO y CLIENTE siempre (en 0 si no hay); un usuario con varios roles cuenta en cada uno.
- registrations: altas por periodo de los usuarios que siguen en la tienda.
 */
public record UserDashboardDto(
        long total,
        long active,
        int activeWindowDays,
        Map<String, Long> byRole,
        RegistrationsDto registrations
) {
}
