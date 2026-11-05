package com.kleinkauf.users.services;

import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.dtos.RolePermissionDto;
import com.kleinkauf.users.entities.RolePermission;
import com.kleinkauf.users.repositories.RolePermissionRepository;
import com.kleinkauf.users.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/*
Permissions per role of each store (PermissionArea bit mask).
- ADMINISTRADOR: always all of them, not configurable.
- EMPLEADO and CLIENTE: configured by the administrator. Without configuration they use the
  permissions from before this existed: EMPLEADO only STOCK, CLIENTE none.
- USUARIOS is not delegable: it stays only for ADMINISTRADOR.
 */
@Service
public class RolePermissionService {

    static final Map<RolesType, Integer> DEFAULTS = Map.of(
            RolesType.EMPLEADO, PermissionArea.STOCK.bit(),
            RolesType.CLIENTE, 0
    );

    // What can be granted to EMPLEADO or CLIENTE: everything except USUARIOS
    static final int DELEGABLE = PermissionArea.ALL & ~PermissionArea.USUARIOS.bit();

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private CacheUtil cacheUtil;

    public int maskFor(String role) {
        RolesType type;
        try {
            type = RolesType.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            return 0;
        }
        return switch (type) {
            case ADMINISTRADOR -> PermissionArea.ALL;
            // The superadmin manages the platform, not a store
            case SUPERADMIN -> 0;
            default -> rolePermissionRepository.findByType(type.name())
                    .map(RolePermission::getPermissions)
                    .orElse(DEFAULTS.get(type));
        };
    }

    public List<RolePermissionDto> list() {
        return List.of(
                new RolePermissionDto(RolesType.ADMINISTRADOR.name(), maskFor(RolesType.ADMINISTRADOR.name())),
                new RolePermissionDto(RolesType.EMPLEADO.name(), maskFor(RolesType.EMPLEADO.name())),
                new RolePermissionDto(RolesType.CLIENTE.name(), maskFor(RolesType.CLIENTE.name()))
        );
    }

    public RolePermissionDto update(String role, int permissions) throws ApiException {
        RolesType type;
        try {
            type = RolesType.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_ROLE, HttpStatus.NOT_FOUND);
        }
        if (!DEFAULTS.containsKey(type)) {
            throw new ApiException(ErrorMessages.ROLE_PERMISSIONS_NOT_EDITABLE, HttpStatus.BAD_REQUEST);
        }
        if ((permissions & ~DELEGABLE) != 0) {
            throw new ApiException(ErrorMessages.INVALID_PERMISSIONS, HttpStatus.BAD_REQUEST);
        }

        RolePermission rolePermission = rolePermissionRepository.findByType(type.name()).orElseGet(() -> {
            RolePermission created = new RolePermission();
            created.setType(type.name());
            return created;
        });
        rolePermission.setPermissions(permissions);
        rolePermissionRepository.save(rolePermission);

        // Each user's roles are cached in Redis (products reads them): the store's are deleted
        cacheUtil.deleteTenantUserRoles(TenantContext.getCurrentTenant());
        return new RolePermissionDto(type.name(), permissions);
    }
}
