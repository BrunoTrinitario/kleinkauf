package com.kleinkauf.sales.util;

import com.kleinkauf.common.servlet.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    // Id of the logged-in user (built by HeaderAuthFilter from the gateway header); null if there is none
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return user.getId();
        }
        return null;
    }
}
