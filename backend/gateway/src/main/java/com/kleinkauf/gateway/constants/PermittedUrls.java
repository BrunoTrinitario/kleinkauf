package com.kleinkauf.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    // Rutas de la plataforma (alta de tenants y panel del superadmin): no pertenecen a ningun tenant
    public static String PLATFORM_PATH = "/api/v1/platform/";

    public static boolean isPlatformPath(String path) {
        return path.startsWith(PLATFORM_PATH);
    }

    // Imagenes de productos: archivos publicos con nombre aleatorio, guardados fuera de cualquier tenant.
    // El navegador no manda Origin al cargar un <img>, asi que no se puede resolver el tenant
    public static String PRODUCT_IMAGES_PATH = "/products/images/";

    // WebSocket de observability: el navegador no puede mandar el JWT en el handshake, entra con un ticket
    public static String WS_PATH = "/ws";

    // Link para compartir un producto (WhatsApp, Instagram): /share/{identificador}/products/{slug}.
    // El crawler no manda Origin: la tienda sale del identificador de la ruta (ver TenantGatewayFilter)
    public static final java.util.regex.Pattern SHARE_PATH =
            java.util.regex.Pattern.compile("^/share/([a-z][a-z0-9-]{1,28}[a-z0-9])/products/[^/]+$");

    // Rutas que no pertenecen a ningun tenant: el gateway no lo busca ni agrega el header
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
    Rutas publicas en las que el login es opcional: si viene un token valido el gateway pasa el usuario
    (crear un pedido logueado lo asocia al usuario). Tienen que estar tambien en getExcludedPaths.
     */
    public static List<String> getOptionalAuthPaths() {
        return List.of(
                "POST:/api/v1/sales/orders",
                // Reportar un bug: con la sesion abierta queda quien lo mando
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

            // Convertir placeholders a regex. {code}: un segmento (ej. numero de pedido K7QM-4X2P)
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
