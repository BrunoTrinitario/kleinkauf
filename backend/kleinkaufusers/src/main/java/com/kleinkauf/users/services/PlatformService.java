package com.kleinkauf.users.services;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.dtos.LoggedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/*
Operaciones de la plataforma (fuera de cualquier tenant): corren en el schema public.
El gateway no agrega X-Forwarded-Host en /api/v1/platform/** y borra el que mande el cliente;
igual se verifica aca para no depender solo del gateway.
 */
@Service
public class PlatformService {

    @Autowired
    private UserService userService;

    public void requirePlatformContext() throws ApiException {
        if (TenantContext.getCurrentTenant() != null) {
            throw new ApiException(ErrorMessages.PLATFORM_ONLY, HttpStatus.NOT_FOUND);
        }
    }

    // Login del superadmin: mismo login que las tiendas pero en public y exige el rol SUPERADMIN
    public LoggedUser login(String email, String password) throws ApiException {
        requirePlatformContext();
        return requireSuperadmin(userService.login(email, password), ErrorMessages.INVALID_CREDENTIALS);
    }

    // Renovar la sesion del superadmin: si lo desactivaron ya no tiene el rol, y no se renueva
    public LoggedUser refresh(String refreshToken) throws ApiException {
        requirePlatformContext();
        return requireSuperadmin(userService.refresh(refreshToken), ErrorMessages.INVALID_TOKEN);
    }

    private static LoggedUser requireSuperadmin(LoggedUser user, String error) throws ApiException {
        boolean isSuperadmin = user.roles().stream()
                .map(RoleDto::getType)
                .anyMatch(RolesType.SUPERADMIN.name()::equals);
        if (!isSuperadmin) {
            throw new ApiException(error, HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
