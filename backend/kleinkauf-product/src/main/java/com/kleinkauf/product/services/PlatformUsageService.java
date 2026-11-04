package com.kleinkauf.product.services;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.platform.ProductUsageDto;
import com.kleinkauf.product.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/*
Per-tenant usage for the platform dashboard: non-deleted products and bundles of each schema.
- Schemas come from the database itself (those that have the product table), not from users: products creates the schema on the
  store's first request, so a tenant that was never opened does not appear and the frontend counts it as 0.
  It does not depend on users answering either. Deleted tenants may appear: the frontend only keeps the
  ones users lists.
- Plain SQL (without Hibernate, which only sees the current tenant's schema). Bundles are hard-deleted: no deleted_at.
- Kept for 5 minutes in Redis; if Redis fails it is computed anyway.
 */
@Service
public class PlatformUsageService {

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]*$");

    public enum UsageSort { PRODUCTS, BUNDLES }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheUtil cacheUtil;

    // sort: products (default) or bundles; highest to lowest
    public List<ProductUsageDto> usage(String sort) throws ApiException {
        UsageSort usageSort = parseSort(sort);
        List<ProductUsageDto> usage = cacheUtil.getPlatformUsage();
        if (usage == null) {
            usage = computeUsage();
            cacheUtil.savePlatformUsage(usage);
        }
        Comparator<ProductUsageDto> primary = usageSort == UsageSort.BUNDLES
                ? Comparator.comparingLong(ProductUsageDto::bundles).reversed()
                : Comparator.comparingLong(ProductUsageDto::products).reversed();
        List<ProductUsageDto> sorted = new ArrayList<>(usage);
        sorted.sort(primary.thenComparing(ProductUsageDto::schema));
        return sorted;
    }

    private List<ProductUsageDto> computeUsage() {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'product' ORDER BY table_schema",
                String.class);
        Set<String> withBundles = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'bundles'", String.class));

        List<ProductUsageDto> usage = new ArrayList<>();
        for (String schema : schemas) {
            // public is not a store; the rest is validated before putting it in the SQL
            if (TenantConstants.PUBLIC.equals(schema) || !SCHEMA.matcher(schema).matches()) continue;
            long products = count("SELECT COUNT(*) FROM " + schema + ".product WHERE deleted_at IS NULL");
            long bundles = withBundles.contains(schema) ? count("SELECT COUNT(*) FROM " + schema + ".bundles") : 0;
            usage.add(new ProductUsageDto(schema, products, bundles));
        }
        return usage;
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private static UsageSort parseSort(String sort) throws ApiException {
        if (sort == null || sort.isBlank()) return UsageSort.PRODUCTS;
        try {
            return UsageSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_USAGE_SORT, HttpStatus.BAD_REQUEST);
        }
    }
}
