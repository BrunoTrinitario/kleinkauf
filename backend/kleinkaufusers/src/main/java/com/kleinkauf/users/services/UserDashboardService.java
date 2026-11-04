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
Panel de la tienda (docs/panel-admin.md): usuarios, activos, por rol y altas por periodo. Siempre al dia (sin cache).
Las consultas van contra el schema de la tienda de la request; los borrados (soft delete) no cuentan.
 */
@Service
public class UserDashboardService {

    // Los roles de una tienda: SUPERADMIN solo existe en public
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
