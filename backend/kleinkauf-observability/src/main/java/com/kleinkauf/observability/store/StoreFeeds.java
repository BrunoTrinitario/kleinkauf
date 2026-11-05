package com.kleinkauf.observability.store;

import com.kleinkauf.observability.collector.Traffic.TenantRates;
import com.kleinkauf.observability.dto.StoreUsageDto;
import com.kleinkauf.observability.dto.StoreUsersDto;
import com.kleinkauf.observability.store.CatalogUsageReader.CatalogUsage;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/*
Store channels built on each collector reading (store.usage and store.users) and their GETs.
- The collector passes it the gateway's per-store traffic (update) and this sends it to the stores with some WS
  subscribed. Stores with nobody watching cost nothing.
- If Redis fails that round nothing is sent to the channel (and the GET responds 503); the next one tries again.
 */
@Component
public class StoreFeeds {

    // Last per-store traffic; null if there are not yet two gateway readings
    private final AtomicReference<Map<String, TenantRates>> traffic = new AtomicReference<>();
    private final SessionRegistry sessions;
    private final StoreUsers storeUsers;
    private final CatalogUsageReader catalogUsage;
    private final Clock clock;

    public StoreFeeds(SessionRegistry sessions, StoreUsers storeUsers, CatalogUsageReader catalogUsage, Clock clock) {
        this.sessions = sessions;
        this.storeUsers = storeUsers;
        this.catalogUsage = catalogUsage;
        this.clock = clock;
    }

    // rates: null if the gateway traffic could not be computed in this reading
    public void update(Map<String, TenantRates> rates) {
        traffic.set(rates == null ? null : Map.copyOf(rates));
        for (String tenant : sessions.tenantsSubscribedTo(Channels.STORE_USERS)) {
            StoreUsersDto users = users(tenant);
            if (users != null) sessions.publishToTenant(Channels.STORE_USERS, tenant, users);
        }
        for (String tenant : sessions.tenantsSubscribedTo(Channels.STORE_USAGE)) {
            StoreUsageDto usage = usage(tenant);
            if (usage != null) sessions.publishToTenant(Channels.STORE_USAGE, tenant, usage);
        }
    }

    // null if Redis could not be read
    public StoreUsersDto users(String tenant) {
        return storeUsers.connected(tenant);
    }

    // null if Redis could not be read
    public StoreUsageDto usage(String tenant) {
        CatalogUsage catalog = catalogUsage.today(tenant);
        if (catalog == null) {
            return null;
        }
        Map<String, TenantRates> rates = traffic.get();
        StoreUsageDto.Traffic tenantTraffic = null;
        if (rates != null) {
            TenantRates r = rates.getOrDefault(tenant, TenantRates.ZERO);
            tenantTraffic = new StoreUsageDto.Traffic(r.requestsPerSecond(), r.errors4xxPerSecond(), r.errors5xxPerSecond());
        }
        return new StoreUsageDto(Instant.now(clock), catalog.date(), catalog.visits(),
                List.copyOf(catalog.topProducts()), tenantTraffic);
    }
}
