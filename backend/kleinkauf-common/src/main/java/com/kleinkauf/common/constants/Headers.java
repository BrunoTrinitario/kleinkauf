package com.kleinkauf.common.constants;

/*
Custom headers that travel between the gateway and the microservices
 */
public final class Headers {
    public static final String FORWARDED_HOST = "X-Forwarded-Host";
    public static final String USER_ID = "X-User-Id";
    public static final String INTERNAL_REQUEST = "X-internal-request";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";
    public static final String OPEN_PATH = "X-Open-Path";
    public static final String TENANT_NAME = "X-Tenant-Name";
    // Client IP according to the gateway (for the audit); the one sent by the client is removed
    public static final String CLIENT_IP = "X-Client-Ip";

    private Headers() {
    }
}
