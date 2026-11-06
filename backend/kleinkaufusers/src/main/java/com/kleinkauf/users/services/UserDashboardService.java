package com.kleinkauf.users.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.dtos.dashboard.UserDashboardDto;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.util.DashboardTime;
import com.kleinkauf.users.util.Periods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
Store dashboard (docs/features/admin-dashboards.md): users, active, by role and sign-ups per period. Always up to date (no cache).
Queries go against the schema of the request's store; deleted ones (soft delete) do not count.
 */
@Service
public class UserDashboardService {

    // A store's roles: SUPERADMIN only exists in public
    private static final List<String> STORE_ROLES = List.of(
            RolesType.ADMINISTRADOR.name(), RolesType.EMPLEADO.name(), RolesType.CLIENTE.name());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DashboardTime dashboardTime;

    public UserDashboardDto dashboard(String from, String to, String groupBy) throws ApiException {
        Periods.Range range = Periods.parse(from, to, groupBy, dashboardTime.today());
        int window = PlatformDashboardService.ACTIVE_WINDOW_DAYS;

        Map<String, Long> byRole = new LinkedHashMap<>();
        STORE_ROLES.forEach(role -> byRole.put(role, 0L));
        for (Object[] row : roleRepository.countUsersByType()) {
            byRole.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<LocalDate> createdDates = userRepository
                .findCreatedAtBetween(dashboardTime.startOfDay(range.from()), dashboardTime.startOfDay(range.to().plusDays(1)))
                .stream()
                .map(dashboardTime::dayOf)
                .toList();

        return new UserDashboardDto(
                userRepository.count(),
                userRepository.countByLastLoginGreaterThanEqual(LocalDateTime.now().minusDays(window)),
                window,
                byRole,
                Periods.series(range, createdDates));
    }
}
