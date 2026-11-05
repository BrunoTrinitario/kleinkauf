package com.kleinkauf.common.constants;

import java.time.Duration;

/*
Last activity in Redis (docs/observabilidad.md), written by the gateway and read by observability.
Online = some request within the last TTL: the key expires on its own.
- Logged-in users: activity:<tenant>:<userId> with the time of their last request (epoch in milliseconds).
  tenant: the store's schema, or "public" for superadmins.
- Anonymous visitors of a store: visitor:<tenant>:<visitorId>. visitorId is a hash of the IP and the browser (the
  IP is not stored); two people behind the same connection and browser count as one.
 */
public final class ActivityKeys {

    public static final String PREFIX = "activity:";
    public static final String VISITOR_PREFIX = "visitor:";
    public static final Duration TTL = Duration.ofMinutes(5);

    private ActivityKeys() {
    }

    public static String key(String tenant, Long userId) {
        return PREFIX + tenant + ":" + userId;
    }

    public static String visitorKey(String tenant, String visitorId) {
        return VISITOR_PREFIX + tenant + ":" + visitorId;
    }

    // The tenant of a key, or null if it does not have the activity:<tenant>:<userId> shape
    public static String tenantOf(String key) {
        return tenantOf(key, PREFIX);
    }

    // The tenant of a visitor:<tenant>:<visitorId> key, or null
    public static String visitorTenantOf(String key) {
        return tenantOf(key, VISITOR_PREFIX);
    }

    private static String tenantOf(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return null;
        }
        String rest = key.substring(prefix.length());
        int colon = rest.lastIndexOf(':');
        return colon <= 0 ? null : rest.substring(0, colon);
    }
}
