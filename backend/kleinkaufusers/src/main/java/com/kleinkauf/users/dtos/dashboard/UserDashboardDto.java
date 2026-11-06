package com.kleinkauf.users.dtos.dashboard;

import com.kleinkauf.users.dtos.platform.RegistrationsDto;

import java.util.Map;

/*
GET /api/v1/users/dashboard: store dashboard (ADMINISTRADOR only, docs/features/admin-dashboards.md).
- active: logged in within the last activeWindowDays days (lastLogin).
- byRole: ADMINISTRADOR, EMPLEADO and CLIENTE always (0 if there are none); a user with several roles counts in each one.
- registrations: sign-ups per period of the users still in the store.
 */
public record UserDashboardDto(
        long total,
        long active,
        int activeWindowDays,
        Map<String, Long> byRole,
        RegistrationsDto registrations
) {
}
