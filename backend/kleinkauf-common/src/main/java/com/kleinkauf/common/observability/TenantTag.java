package com.kleinkauf.common.observability;

import java.util.regex.Pattern;

/*
Value of the "tenant" tag on the metrics: the store's schema.
A tag with unbounded values would flood Prometheus with series, so only something shaped
like a schema is accepted; anything else is grouped under "invalid". Without a tenant (platform, images): "none".
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
