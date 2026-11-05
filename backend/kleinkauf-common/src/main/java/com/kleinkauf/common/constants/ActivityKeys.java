package com.kleinkauf.common.constants;

import java.time.Duration;

/*
Last activity of each user in Redis (docs/observabilidad.md): activity:<tenant>:<userId> with the time
of their last request (epoch in milliseconds). Written by the gateway and read by observability.
Online = some request within the last TTL: the key expires on its own.
tenant: the store's schema, or "public" for superadmins.
 */
public final class ActivityKeys {

    public static final String PREFIX = "activity:";
    public static final Duration TTL = Duration.ofMinutes(5);

    private ActivityKeys() {
    }

    public static String key(String tenant, Long userId) {
        return PREFIX + tenant + ":" + userId;
    }

    // The tenant of a key, or null if it does not have the activity:<tenant>:<userId> shape
    public static String tenantOf(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        String rest = key.substring(PREFIX.length());
        int colon = rest.lastIndexOf(':');
        return colon <= 0 ? null : rest.substring(0, colon);
    }
}
