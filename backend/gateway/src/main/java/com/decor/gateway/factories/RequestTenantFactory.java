package com.decor.gateway.factories;

public class RequestTenantFactory {
    public static final String LOGIN = "login";
    public static final String SIGNUP = "signup";
    public static final String GLOBAL = "global";
    // Las imagenes del catalogo no cuentan para el rate limit
    public static final String IMAGE = "image";
    // Checkout: crear pedidos y consultar un pedido como invitado (limites propios)
    public static final String ORDER = "order";
    public static final String LOOKUP = "lookup";

    public static String generatePathType(String path){
        String result;
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/platform/auth/login"))
            result = LOGIN;
        else if (path.startsWith("/api/v1/platform/tenants"))
            result = SIGNUP;
        else if (path.startsWith("/products/images/"))
            result = IMAGE;
        else if (path.equals("/api/v1/sales/orders"))
            result = ORDER;
        else if (path.equals("/api/v1/sales/orders/lookup"))
            result = LOOKUP;
        else
            result = GLOBAL;

        return result;
    }
}
