package com.kleinkauf.gateway.factories;

public class RequestTenantFactory {
    public static final String LOGIN = "login";
    public static final String SIGNUP = "signup";
    public static final String GLOBAL = "global";
    // Las imagenes del catalogo no cuentan para el rate limit
    public static final String IMAGE = "image";
    // Checkout: crear pedidos, consultar o cancelar un pedido como invitado y subir comprobantes (limites propios)
    public static final String ORDER = "order";
    public static final String LOOKUP = "lookup";
    public static final String RECEIPT = "receipt";

    public static String generatePathType(String path){
        String result;
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/platform/auth/login"))
            result = LOGIN;
        // Registro y recuperacion de contrasena comparten el limite del alta de tiendas: cada recuperacion manda un
        // mail, y con el limite global se podria llenar de mails la casilla de alguien
        // Los reportes de bugs tambien: son publicos y le llegan a los superadmins
        else if (path.startsWith("/api/v1/platform/tenants") || path.equals("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/forgot-password") || path.equals("/api/v1/bug-reports"))
            result = SIGNUP;
        else if (path.startsWith("/products/images/"))
            result = IMAGE;
        else if (path.equals("/api/v1/sales/orders"))
            result = ORDER;
        // Cancelar tambien pide el email: comparte el limite de la consulta para que no se puedan probar emails
        else if (path.equals("/api/v1/sales/orders/lookup") || path.matches("^/api/v1/sales/orders/[^/]+/cancel$"))
            result = LOOKUP;
        else if (path.matches("^/api/v1/sales/orders/[^/]+/receipt$"))
            result = RECEIPT;
        else
            result = GLOBAL;

        return result;
    }
}
