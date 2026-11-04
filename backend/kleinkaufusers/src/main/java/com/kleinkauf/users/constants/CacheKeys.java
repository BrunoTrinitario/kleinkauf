package com.kleinkauf.users.constants;

public class CacheKeys {
    public static String GATEWAY_CORS = "gateway:cors:";
    public static String CACHE_USER(Long userId, String tenantSchema){
        return "user:" + userId + ":" + tenantSchema;
    }
    // Valid refresh tokens: one per open session. If it is not there, the refresh does not work (logout or already rotated)
    public static String REFRESH_TOKEN(String tenantSchema, Long userId, String tokenId){
        return "refresh:" + tenantSchema + ":" + userId + ":" + tokenId;
    }
    public static String CACHE_TENANT = "tenant:";
    // The gateway looks up the tenant by the frontend URL (Origin)
    public static String CACHE_TENANT_URL = "tenant:url:";
    // Per-tenant usage for the platform dashboard (products stores its own in platform:usage:products)
    public static String PLATFORM_USAGE_USERS = "platform:usage:users";

}
