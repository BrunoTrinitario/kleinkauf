package com.decor.users.constants;

public class CacheKeys {
    public static String GATEWAY_CORS = "gateway:cors:";
    public static String CACHE_USER(Long userId, String tenantSchema){
        return "user:" + userId + ":" + tenantSchema;
    }
    public static String CACHE_TENANT = "tenant:";

}
