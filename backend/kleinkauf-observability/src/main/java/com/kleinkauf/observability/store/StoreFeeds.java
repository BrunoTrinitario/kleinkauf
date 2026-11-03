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
Canales de la tienda que se arman en cada lectura del recolector (store.usage y store.users) y sus GET.
- El recolector le pasa el trafico por tienda del gateway (update) y esto lo manda a las tiendas con algun WS
  suscripto. Las tiendas sin nadie mirando no cuestan nada.
- Si Redis falla esa vuelta no se manda nada al canal (y el GET responde 503); la siguiente vuelve a intentar.
 */
@Component
public class StoreFeeds {

    // Ultimo trafico por tienda; null si todavia no hay dos lecturas del gateway
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

    // rates: null si en esta lectura no se pudo calcular el trafico del gateway
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

    // null si no se pudo leer Redis
    public StoreUsersDto users(String tenant) {
        return storeUsers.connected(tenant);
    }

    // null si no se pudo leer Redis
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
