package com.kleinkauf.observability.ws;

import java.util.Set;

/*
WS channels (docs/features/observability.md).
Platform (superadmin, public ticket):
- platform.health: the services' health, when some status changes.
- platform.traffic: traffic, resources and online users, on each collector reading.
- platform.business: store creations, deletions and changes (users events).
- platform.audit: the platform audit, as soon as it is stored.
Store (administrator, ticket for their store): always the ticket's store, another one cannot be requested.
- store.audit: their store's audit, as soon as it is stored.
- store.activity: new orders, in-store sales, receipts and stock (sales and products events), as they happen.
- store.usage: catalog visits, the day's most viewed products and requests and errors, on each collector reading.
- store.users: the online users (some request in the last 5 minutes), on each collector reading.
 */
public final class Channels {

    public static final String PLATFORM_HEALTH = "platform.health";
    public static final String PLATFORM_TRAFFIC = "platform.traffic";
    public static final String PLATFORM_BUSINESS = "platform.business";
    public static final String PLATFORM_AUDIT = "platform.audit";
    public static final String STORE_AUDIT = "store.audit";
    public static final String STORE_ACTIVITY = "store.activity";
    public static final String STORE_USAGE = "store.usage";
    public static final String STORE_USERS = "store.users";

    static final Set<String> PLATFORM = Set.of(PLATFORM_HEALTH, PLATFORM_TRAFFIC, PLATFORM_BUSINESS, PLATFORM_AUDIT);
    static final Set<String> STORE = Set.of(STORE_AUDIT, STORE_ACTIVITY, STORE_USAGE, STORE_USERS);

    private Channels() {
    }

    public static boolean allowed(WsTicket ticket, String channel) {
        return ticket.isPlatform() ? PLATFORM.contains(channel) : STORE.contains(channel);
    }
}
