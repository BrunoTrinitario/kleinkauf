package com.kleinkauf.users.constants;

public class CacheKeys {
    public static String GATEWAY_CORS = "gateway:cors:";
    public static String CACHE_USER(Long userId, String tenantSchema){
        return "user:" + userId + ":" + tenantSchema;
    }
    // Refresh tokens vigentes: uno por sesion abierta. Si no esta, el refresh no sirve (logout o ya rotado)
    public static String REFRESH_TOKEN(String tenantSchema, Long userId, String tokenId){
        return "refresh:" + tenantSchema + ":" + userId + ":" + tokenId;
    }
    public static String CACHE_TENANT = "tenant:";
    // El gateway busca el tenant por la URL del frontend (Origin)
    public static String CACHE_TENANT_URL = "tenant:url:";
    // Uso por tenant del panel de la plataforma (products guarda el suyo en platform:usage:products)
    public static String PLATFORM_USAGE_USERS = "platform:usage:users";

}
