package com.decor.common.constants;

public final class TenantConstants {
    // Schema de la plataforma (tabla de tenants y superadmins); tambien el default sin tenant
    public static final String PUBLIC = "public";

    // Claim del JWT con el schema del tenant al que pertenece el token
    public static final String TOKEN_CLAIM = "tenant";

    private TenantConstants() {
    }
}
