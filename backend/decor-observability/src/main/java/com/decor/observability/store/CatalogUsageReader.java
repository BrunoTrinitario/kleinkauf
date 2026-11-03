package com.decor.observability.store;

import com.decor.common.constants.UsageKeys;
import com.decor.observability.dto.StoreUsageDto.ProductViews;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/*
Uso del catalogo de una tienda en el dia: el hash usage:<tenant>:<dia> que escribe products (ver UsageKeys).
 */
@Slf4j
@Component
public class CatalogUsageReader {

    static final int TOP_PRODUCTS = 5;

    public record CatalogUsage(LocalDate date, long visits, List<ProductViews> topProducts) {
    }

    private final StringRedisTemplate redis;

    public CatalogUsageReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // null si no se pudo leer Redis
    public CatalogUsage today(String tenant) {
        LocalDate today = UsageKeys.today();
        Map<Object, Object> counts;
        try {
            counts = redis.opsForHash().entries(UsageKeys.key(tenant, today));
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el uso del catalogo de {}: {}", tenant, e.toString());
            return null;
        }
        long visits = 0;
        List<ProductViews> products = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : counts.entrySet()) {
            String field = String.valueOf(entry.getKey());
            long count = parse(entry.getValue());
            if (UsageKeys.VISITS.equals(field)) {
                visits = count;
            } else if (field.startsWith(UsageKeys.PRODUCT_PREFIX)) {
                products.add(new ProductViews(field.substring(UsageKeys.PRODUCT_PREFIX.length()), count));
            }
        }
        List<ProductViews> top = products.stream()
                .sorted(Comparator.comparingLong(ProductViews::views).reversed().thenComparing(ProductViews::slug))
                .limit(TOP_PRODUCTS)
                .toList();
        return new CatalogUsage(today, visits, top);
    }

    private static long parse(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
