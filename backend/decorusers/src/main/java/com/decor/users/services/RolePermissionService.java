package com.decor.users.services;

import com.decor.common.security.PermissionArea;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.users.constants.ErrorMessages;
import com.decor.users.constants.RolesType;
import com.decor.users.dtos.RolePermissionDto;
import com.decor.users.entities.RolePermission;
import com.decor.users.repositories.RolePermissionRepository;
import com.decor.users.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/*
Permisos por rol de cada tienda (mascara de bits de PermissionArea).
- ADMINISTRADOR: siempre todos, no se configura.
- EMPLEADO y CLIENTE: los configura el administrador. Sin configuracion usan los
  permisos de antes de existir esto: EMPLEADO solo STOCK, CLIENTE ninguno.
- USUARIOS no se delega: queda solo para ADMINISTRADOR.
 */
@Service
public class RolePermissionService {

    static final Map<RolesType, Integer> DEFAULTS = Map.of(
            RolesType.EMPLEADO, PermissionArea.STOCK.bit(),
            RolesType.CLIENTE, 0
    );

    // Lo que se le puede dar a EMPLEADO o CLIENTE: todo menos USUARIOS
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
            // El superadmin administra la plataforma, no una tienda
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

        // Los roles de cada usuario estan cacheados en Redis (los lee products): se borran los de la tienda
        cacheUtil.deleteTenantUserRoles(TenantContext.getCurrentTenant());
        return new RolePermissionDto(type.name(), permissions);
    }
}
