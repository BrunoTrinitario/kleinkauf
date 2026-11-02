package com.decor.users.services;

import com.decor.common.dto.RoleDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.users.constants.ErrorMessages;
import com.decor.users.constants.RolesType;
import com.decor.users.dtos.LoggedUser;
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
        LoggedUser user = userService.login(email, password);
        boolean isSuperadmin = user.roles().stream()
                .map(RoleDto::getType)
                .anyMatch(RolesType.SUPERADMIN.name()::equals);
        if (!isSuperadmin) {
            throw new ApiException(ErrorMessages.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
