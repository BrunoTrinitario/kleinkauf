package com.kleinkauf.users.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";
    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/platform/tenants",
                "/api/v1/platform/auth/login",
                // Renewing the session: the 1-hour token may have expired
                "/api/v1/auth/refresh",
                "/api/v1/platform/auth/refresh",
                // Logout: only sends the refresh token, which is what it revokes
                "/api/v1/auth/logout",
                "/api/v1/platform/auth/logout",
                // Reporting a bug: with or without a session. The controller reads the user from the gateway header
                "/api/v1/bug-reports",
                // Profile photos: public files, nobody is logged in when an <img> loads them
                "/users/avatars/"
        );
    }

    public static boolean isPermitted(String path) {
        for(String allowed : PermittedUrls.getExcludedPaths()) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
