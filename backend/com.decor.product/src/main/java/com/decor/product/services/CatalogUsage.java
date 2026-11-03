package com.decor.product.services;

import com.decor.common.constants.UsageKeys;
import com.decor.common.servlet.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
Uso del catalogo publico para el panel de la tienda (canal store.usage, ver UsageKeys): visitas al listado y
vistas del detalle de cada producto, contadas por dia en Redis. observability las lee.
Es un HINCRBY por request; si Redis falla se loguea y el catalogo responde igual.
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
            // La primera escritura del dia le pone el vencimiento
            if (count != null && count == 1) {
                template.expire(key, UsageKeys.TTL);
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo contar el uso del catalogo ({}): {}", key, e.toString());
        }
    }
}
