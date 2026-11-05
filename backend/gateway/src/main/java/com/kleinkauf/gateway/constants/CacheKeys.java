package com.kleinkauf.gateway.constants;

public class CacheKeys {
    public static String GATEWAY_CORS = "gateway:cors:";
    public static String TENANT_CACHE = "tenant:";
    // users stores the tenant by the frontend URL (Origin)
    public static String TENANT_URL_CACHE = "tenant:url:";
}
