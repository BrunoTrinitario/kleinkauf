package com.decor.product.services;

import com.decor.common.constants.TenantConstants;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.platform.ProductUsageDto;
import com.decor.product.util.CacheUtil;
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
Uso por tenant para el panel de la plataforma: productos no borrados y combos de cada schema.
- Los schemas salen de la propia base (los que tienen la tabla product), no de users: products crea el schema en la
  primera request de la tienda, asi que un tenant que nunca se abrio no aparece y el frontend lo cuenta como 0.
  Tampoco depende de que users responda. Los tenants borrados pueden aparecer: el frontend se queda solo con los
  que lista users.
- SQL directo (sin Hibernate, que solo ve el schema del tenant actual). Los combos se borran de verdad: sin deleted_at.
- Se guarda 5 minutos en Redis; si Redis falla se calcula igual.
 */
@Service
public class PlatformUsageService {

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]*$");

    public enum UsageSort { PRODUCTS, BUNDLES }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheUtil cacheUtil;

    // sort: products (por defecto) o bundles; de mayor a menor
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
            // public no es una tienda; el resto se valida antes de meterlo en el SQL
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
