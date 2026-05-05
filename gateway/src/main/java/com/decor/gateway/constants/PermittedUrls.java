package com.decor.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

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
