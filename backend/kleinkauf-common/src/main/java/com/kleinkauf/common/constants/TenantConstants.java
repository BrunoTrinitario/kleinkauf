package com.kleinkauf.common.constants;

public final class TenantConstants {
    // Platform schema (tenants and superadmins table); also the default without a tenant
    public static final String PUBLIC = "public";

    // JWT claim with the schema of the tenant the token belongs to
    public static final String TOKEN_CLAIM = "tenant";

    private TenantConstants() {
    }
}
