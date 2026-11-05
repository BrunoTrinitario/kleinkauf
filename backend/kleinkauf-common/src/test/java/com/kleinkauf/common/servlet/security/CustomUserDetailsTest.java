package com.kleinkauf.common.servlet.security;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.security.PermissionArea;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUserDetailsTest {

    @Test
    void rolesAndPermissionsFromTheUnionOfTheirMasks() {
        CustomUserDetails user = new CustomUserDetails(1L, List.of(
                new RoleDto("EMPLEADO", PermissionArea.STOCK.bit()),
                new RoleDto("CLIENTE", PermissionArea.PRECIOS.bit())));

        Set<String> authorities = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertEquals(Set.of("ROLE_EMPLEADO", "ROLE_CLIENTE", "PERM_STOCK", "PERM_PRECIOS"), authorities);
        assertTrue(user.hasPermission(PermissionArea.STOCK));
        assertFalse(user.hasPermission(PermissionArea.PRODUCTOS));
    }

    @Test
    void fullMask_allAreas() {
        CustomUserDetails admin = new CustomUserDetails(1L, List.of(new RoleDto("ADMINISTRADOR", PermissionArea.ALL)));

        for (PermissionArea area : PermissionArea.values()) {
            assertTrue(admin.hasPermission(area), area.name());
        }
    }

    @Test
    void bits() {
        assertEquals(Set.of(PermissionArea.PRODUCTOS, PermissionArea.STOCK), PermissionArea.fromMask(5));
        assertEquals(Set.of(), PermissionArea.fromMask(0));
        assertEquals(31, PermissionArea.ALL);
    }
}
