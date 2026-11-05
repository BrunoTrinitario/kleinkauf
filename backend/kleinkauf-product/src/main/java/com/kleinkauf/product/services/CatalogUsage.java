package com.kleinkauf.product.services;

import com.kleinkauf.common.constants.UsageKeys;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
Public catalog usage for the store dashboard (store.usage channel, see UsageKeys): listing visits and
detail views of each product, counted per day in Redis. observability reads them.
It is one HINCRBY per request; if Redis fails it is logged and the catalog responds anyway.
 */
@Slf4j
@Component
public class CatalogUsage {

    private final ObjectProvider<StringRedisTemplate> redis;

    public CatalogUsage(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    public void visit() {
        increment(UsageKeys.VISITS);
    }

    public void productView(String slug) {
        if (slug != null && !slug.isBlank()) {
            increment(UsageKeys.productField(slug));
        }
    }

    private void increment(String field) {
        String tenant = TenantContext.getCurrentTenant();
        StringRedisTemplate template = redis.getIfAvailable();
        if (tenant == null || template == null) {
            return;
        }
        String key = UsageKeys.key(tenant, UsageKeys.today());
        try {
            Long count = template.opsForHash().increment(key, field, 1);
            // The first write of the day sets the expiry
            if (count != null && count == 1) {
                template.expire(key, UsageKeys.TTL);
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo contar el uso del catalogo ({}): {}", key, e.toString());
        }
    }
}
