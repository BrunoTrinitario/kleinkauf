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
Platform operations (outside any tenant): they run in the public schema.
The gateway does not add X-Forwarded-Host on /api/v1/platform/** and removes the one the client sends;
it is still checked here so as not to depend only on the gateway.
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

    // Superadmin login: same login as the stores but in public, and it requires the SUPERADMIN role
    public LoggedUser login(String email, String password) throws ApiException {
        requirePlatformContext();
        return requireSuperadmin(userService.login(email, password), ErrorMessages.INVALID_CREDENTIALS);
    }

    // Renewing the superadmin session: if they were deactivated they no longer have the role, and it is not renewed
    public LoggedUser refresh(String refreshToken) throws ApiException {
        requirePlatformContext();
        return requireSuperadmin(userService.refresh(refreshToken), ErrorMessages.INVALID_TOKEN);
    }

    public void logout(String refreshToken) throws ApiException {
        requirePlatformContext();
        userService.logout(refreshToken);
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
