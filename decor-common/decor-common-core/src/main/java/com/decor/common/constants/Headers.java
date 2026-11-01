package com.decor.common.constants;

/*
Headers propios que viajan entre el gateway y los microservicios
 */
public final class Headers {
    public static final String FORWARDED_HOST = "X-Forwarded-Host";
    public static final String USER_ID = "X-User-Id";
    public static final String INTERNAL_REQUEST = "X-internal-request";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";
    public static final String OPEN_PATH = "X-Open-Path";
    public static final String TENANT_NAME = "X-Tenant-Name";

    private Headers() {
    }
}
