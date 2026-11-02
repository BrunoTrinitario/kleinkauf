package com.decor.product.util;

import com.decor.common.security.PermissionArea;
import com.decor.common.servlet.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    // Id del usuario logueado (lo arma HeaderAuthFilter con el header del gateway); null si no hay
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return user.getId();
        }
        return null;
    }

    public static boolean hasPermission(PermissionArea area) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CustomUserDetails user && user.hasPermission(area);
    }
}
