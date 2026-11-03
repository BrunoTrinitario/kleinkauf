package com.kleinkauf.common.observability;

import java.util.regex.Pattern;

/*
Valor de la etiqueta "tenant" de las metricas: el schema de la tienda.
Una etiqueta con valores sin limite llenaria Prometheus de series, asi que solo se acepta algo con
forma de schema; lo demas se agrupa en "invalid". Sin tenant (plataforma, imagenes): "none".
 */
public final class TenantTag {

    public static final String KEY = "tenant";
    public static final String NONE = "none";
    public static final String INVALID = "invalid";

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private TenantTag() {
    }

    public static String of(String tenant) {
        if (tenant == null || tenant.isBlank()) return NONE;
        return SCHEMA.matcher(tenant).matches() ? tenant : INVALID;
    }
}
