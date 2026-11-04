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
                // Renovar la sesion: el token de 1 hora puede estar vencido
                "/api/v1/auth/refresh",
                "/api/v1/platform/auth/refresh",
                // Cerrar sesion: solo manda el refresh, que es lo que corta
                "/api/v1/auth/logout",
                "/api/v1/platform/auth/logout",
                // Reportar un bug: con o sin sesion. El controller lee el usuario del header del gateway
                "/api/v1/bug-reports"
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
