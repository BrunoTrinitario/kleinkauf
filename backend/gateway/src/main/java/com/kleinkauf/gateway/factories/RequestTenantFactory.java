package com.kleinkauf.gateway.factories;

public class RequestTenantFactory {
    public static final String LOGIN = "login";
    public static final String SIGNUP = "signup";
    public static final String GLOBAL = "global";
    // Catalog images do not count for the rate limit
    public static final String IMAGE = "image";
    // Checkout: creating orders, looking up or cancelling an order as a guest and uploading receipts (own limits)
    public static final String ORDER = "order";
    public static final String LOOKUP = "lookup";
    public static final String RECEIPT = "receipt";

    public static String generatePathType(String path){
        String result;
        // Renewing the session shares the login limit: it also hands out new tokens
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/platform/auth/login")
                || path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/platform/auth/refresh"))
            result = LOGIN;
        // Sign-up and password recovery share the store sign-up limit: each recovery sends an
        // email, and with the global limit someone's inbox could be flooded with emails
        // Bug reports too: they are public and reach the superadmins
        else if (path.startsWith("/api/v1/platform/tenants") || path.equals("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/forgot-password") || path.equals("/api/v1/bug-reports"))
            result = SIGNUP;
        else if (path.startsWith("/products/images/") || path.startsWith("/users/avatars/"))
            result = IMAGE;
        else if (path.equals("/api/v1/sales/orders"))
            result = ORDER;
        // Cancelling also asks for the email: it shares the lookup limit so emails cannot be probed
        else if (path.equals("/api/v1/sales/orders/lookup") || path.matches("^/api/v1/sales/orders/[^/]+/cancel$"))
            result = LOOKUP;
        else if (path.matches("^/api/v1/sales/orders/[^/]+/receipt$"))
            result = RECEIPT;
        else
            result = GLOBAL;

        return result;
    }
}
