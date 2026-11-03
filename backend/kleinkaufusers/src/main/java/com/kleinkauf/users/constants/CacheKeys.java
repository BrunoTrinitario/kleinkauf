package com.kleinkauf.users.constants;

public class CacheKeys {
    public static String GATEWAY_CORS = "gateway:cors:";
    public static String CACHE_USER(Long userId, String tenantSchema){
        return "user:" + userId + ":" + tenantSchema;
    }
    public static String CACHE_TENANT = "tenant:";
    // El gateway busca el tenant por la URL del frontend (Origin)
    public static String CACHE_TENANT_URL = "tenant:url:";
    // Uso por tenant del panel de la plataforma (products guarda el suyo en platform:usage:products)
    public static String PLATFORM_USAGE_USERS = "platform:usage:users";

}
