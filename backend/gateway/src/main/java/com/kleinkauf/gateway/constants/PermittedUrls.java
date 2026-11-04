package com.kleinkauf.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    // Platform routes (tenant sign-up and superadmin dashboard): they do not belong to any tenant
    public static String PLATFORM_PATH = "/api/v1/platform/";

    public static boolean isPlatformPath(String path) {
        return path.startsWith(PLATFORM_PATH);
    }

    // Product images: public files with a random name, stored outside any tenant.
    // The browser does not send Origin when loading an <img>, so the tenant cannot be resolved
    public static String PRODUCT_IMAGES_PATH = "/products/images/";

    // Observability WebSocket: the browser cannot send the JWT in the handshake, it gets in with a ticket
    public static String WS_PATH = "/ws";

    // Link to share a product (WhatsApp, Instagram): /share/{identifier}/products/{slug}.
    // The crawler does not send Origin: the store comes from the identifier in the path (see TenantGatewayFilter)
    public static final java.util.regex.Pattern SHARE_PATH =
            java.util.regex.Pattern.compile("^/share/([a-z][a-z0-9-]{1,28}[a-z0-9])/products/[^/]+$");

    // Routes that do not belong to any tenant: the gateway neither looks it up nor adds the header
    public static boolean isTenantlessPath(String path) {
        return isPlatformPath(path) || path.startsWith(PRODUCT_IMAGES_PATH);
    }

    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "POST:/api/v1/auth/refresh",
                "POST:/api/v1/auth/forgot-password",
                "POST:/api/v1/auth/forgot-password/{any}",
                "POST:/api/v1/platform/tenants",
                "POST:/api/v1/platform/auth/login",
                "POST:/api/v1/platform/auth/refresh",
                // Logout: revokes the refresh token it sends (the 1-hour token may have expired)
                "POST:/api/v1/auth/logout",
                "POST:/api/v1/platform/auth/logout",
                "GET:/api/v1/products/{id}/metadata",
                "GET:/api/v1/catalog/products",
                "GET:/api/v1/catalog/featured",
                "GET:/api/v1/catalog/products/{any}",
                "GET:/api/v1/catalog/categories",
                "GET:/api/v1/catalog/bundles",
                "GET:/api/v1/sales/checkout",
                "POST:/api/v1/sales/orders",
                "POST:/api/v1/sales/orders/lookup",
                "POST:/api/v1/sales/orders/{code}/receipt",
                "POST:/api/v1/sales/orders/{code}/cancel",
                "POST:/api/v1/bug-reports",
                "/products/images/{any}",
                "GET:/share/{code}/products/{code}",
                "GET:/ws"
        );
    }

    /*
    Public routes where login is optional: if a valid token comes in, the gateway passes the user on
    (creating an order while logged in links it to the user). They must also be in getExcludedPaths.
     */
    public static List<String> getOptionalAuthPaths() {
        return List.of(
                "POST:/api/v1/sales/orders",
                // Reporting a bug: with an open session it records who sent it
                "POST:/api/v1/bug-reports"
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

            // If the rule has the METHOD:/path format
            if (allowed.contains(":")) {
                String[] parts = allowed.split(":", 2);
                allowedMethod = parts[0].toUpperCase();
                allowedPath = parts[1];
            }

            // If it has a method, it must match exactly
            if (allowedMethod != null && !allowedMethod.equalsIgnoreCase(method)) {
                continue;
            }

            // Convert placeholders to regex. {code}: one segment (e.g. order number K7QM-4X2P)
            String regex = allowedPath
                    .replace("{id}", "\\d+")
                    .replace("{code}", "[^/]+")
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
