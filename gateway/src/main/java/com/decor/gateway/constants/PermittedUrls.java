package com.decor.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "GET:/api/v1/products/all",

                "GET:/api/v1/products/{id}",
                "GET:/api/v1/products/{id}/metadata",
                "GET:/api/v1/products/search",
                "/products/images/{any}"
        );
    }

    public static boolean isPermitted(String method, String path) {
        String cleanPath = path.split("\\?")[0]; // remover query params

        for (String allowed : getExcludedPaths()) {
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
