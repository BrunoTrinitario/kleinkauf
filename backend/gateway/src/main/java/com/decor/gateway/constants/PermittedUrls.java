package com.decor.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    // Rutas de la plataforma (alta de tenants y panel del superadmin): no pertenecen a ningun tenant
    public static String PLATFORM_PATH = "/api/v1/platform/";

    public static boolean isPlatformPath(String path) {
        return path.startsWith(PLATFORM_PATH);
    }

    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "POST:/api/v1/auth/forgot-password",
                "POST:/api/v1/auth/forgot-password/{any}",
                "POST:/api/v1/platform/tenants",
                "POST:/api/v1/platform/auth/login",
                "GET:/api/v1/products/all",
                "GET:/api/v1/products/featured",
                "GET:/api/v1/products/{id}",
                "GET:/api/v1/products/{id}/metadata",
                "GET:/api/v1/products/search",
                "GET:/api/v1/catalog/products",
                "GET:/api/v1/catalog/products/{any}",
                "GET:/api/v1/catalog/categories",
                "GET:/api/v1/catalog/bundles",
                "GET:/api/v1/sales/checkout",
                "POST:/api/v1/sales/orders",
                "POST:/api/v1/sales/orders/lookup",
                "GET:/api/v1/bundles/all",
                "GET:/api/v1/bundles/{id}",
                "/products/images/{any}"
        );
    }

    /*
    Rutas publicas en las que el login es opcional: si viene un token valido el gateway pasa el usuario
    (crear un pedido logueado lo asocia al usuario). Tienen que estar tambien en getExcludedPaths.
     */
    public static List<String> getOptionalAuthPaths() {
        return List.of(
                "POST:/api/v1/sales/orders"
        );
    }

    public static boolean isOptionalAuth(String method, String path) {
        return matches(getOptionalAuthPaths(), method, path);
    }

    public static boolean isPermitted(String method, String path) {
        return matches(getExcludedPaths(), method, path);
    }

    private static boolean matches(List<String> rules, String method, String path) {
        String cleanPath = path.split("\\?")[0]; // remover query params

        for (String allowed : rules) {
            String allowedMethod = null;
            String allowedPath = allowed;

            // Si la regla tiene formato METHOD:/ruta
            if (allowed.contains(":")) {
                String[] parts = allowed.split(":", 2);
                allowedMethod = parts[0].toUpperCase();
                allowedPath = parts[1];
            }

            // Si tiene método, debe coincidir exactamente
            if (allowedMethod != null && !allowedMethod.equalsIgnoreCase(method)) {
                continue;
            }

            // Convertir placeholders a regex
            String regex = allowedPath
                    .replace("{id}", "\\d+")
                    .replace("{any}", ".*$")
                    .replace("/", "\\/");

            regex = "^" + regex + "$";

            if (cleanPath.matches(regex)) {
                return true;
            }
        }

        return false;
    }
}
