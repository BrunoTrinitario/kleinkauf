package com.decor.users.constants;

import java.util.List;

public class PermittedUrls {
    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register"
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
