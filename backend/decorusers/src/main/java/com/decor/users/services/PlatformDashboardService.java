package com.decor.users.services;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.constants.ErrorMessages;
import com.decor.users.dtos.platform.PlatformDashboardDto;
import com.decor.users.dtos.platform.TenantCountsDto;
import com.decor.users.dtos.platform.TenantUsageDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.Dates;
import com.decor.users.util.Periods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/*
Panel de la plataforma (superadmin): resumen de tenants y uso por tenant.
- El resumen lee la tabla de tenants de public (incluidos los borrados: es la historia de altas).
- El uso recorre el schema de cada tenant no borrado con SQL directo (sin Hibernate, que solo ve el schema
  del tenant actual) y se guarda 5 minutos en Redis. Si Redis falla, se calcula igual.
Las columnas son las que crea hbm2ddl sin la estrategia de nombres de Spring: lastLogin quedo como "lastlogin".
 */
@Slf4j
@Service
public class PlatformDashboardService {

    public static final int ACTIVE_WINDOW_DAYS = 30;

    // Mismo formato que arma TenantService.toSchema; se valida igual antes de meterlo en el SQL
    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]*$");

    public enum UsageSort { USERS, ACTIVEUSERS, LASTACTIVITY, CREATEDAT }

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheUtil cacheUtil;

    public PlatformDashboardDto dashboard(String from, String to, String groupBy) throws ApiException {
        Periods.Range range = Periods.parse(from, to, groupBy, LocalDate.now());
        List<Tenant> tenants = tenantRepository.findAll();

        long deleted = tenants.stream().filter(t -> t.getDeletedAt() != null).count();
        long enabled = tenants.stream().filter(t -> t.getDeletedAt() == null && Boolean.TRUE.equals(t.getEnable())).count();
        long total = tenants.size() - deleted;
        TenantCountsDto counts = new TenantCountsDto(total, enabled, total - enabled, deleted);

        List<LocalDate> createdDates = tenants.stream()
                .map(Tenant::getCreatedAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .toList();
        return new PlatformDashboardDto(counts, Periods.series(range, createdDates));
    }

    // sort: users (por defecto), activeUsers, lastActivity o createdAt; siempre de mayor a menor
    public List<TenantUsageDto> usage(String sort) throws ApiException {
        UsageSort usageSort = parseSort(sort);
        List<TenantUsageDto> usage = cacheUtil.getPlatformUsage();
        if (usage == null) {
            usage = computeUsage();
            cacheUtil.savePlatformUsage(usage);
        }
        List<TenantUsageDto> sorted = new ArrayList<>(usage);
        sorted.sort(comparator(usageSort));
        return sorted;
    }

    private List<TenantUsageDto> computeUsage() {
        LocalDateTime activeSince = LocalDateTime.now().minusDays(ACTIVE_WINDOW_DAYS);
        // users crea el schema del tenant en el alta, pero un tenant viejo podria no tener la tabla todavia
        Set<String> withUsersTable = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'usuarios'", String.class));

        List<TenantUsageDto> usage = new ArrayList<>();
        for (Tenant tenant : tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()) {
            String schema = tenant.getSchema();
            long users = 0;
            long activeUsers = 0;
            LocalDateTime lastActivity = null;
            if (SCHEMA.matcher(schema).matches() && withUsersTable.contains(schema)) {
                UserCounts counts = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) AS users, COUNT(*) FILTER (WHERE lastlogin >= ?) AS active, MAX(lastlogin) AS last "
                                + "FROM " + schema + ".usuarios WHERE deleted_at IS NULL",
                        (rs, i) -> new UserCounts(rs.getLong("users"), rs.getLong("active"), rs.getTimestamp("last")),
                        Timestamp.valueOf(activeSince));
                if (counts != null) {
                    users = counts.users();
                    activeUsers = counts.active();
                    lastActivity = counts.last() == null ? null : counts.last().toLocalDateTime();
                }
            } else if (!SCHEMA.matcher(schema).matches()) {
                log.warn("Schema invalido en la tabla de tenants, no se cuenta: {}", schema);
            }
            usage.add(new TenantUsageDto(tenant.getId(), schema.replace('_', '-'), schema,
                    Boolean.TRUE.equals(tenant.getEnable()), Dates.withServerOffset(tenant.getCreatedAt()),
                    users, activeUsers, Dates.withServerOffset(lastActivity)));
        }
        return usage;
    }

    private record UserCounts(long users, long active, Timestamp last) {
    }

    private static UsageSort parseSort(String sort) throws ApiException {
        if (sort == null || sort.isBlank()) return UsageSort.USERS;
        try {
            return UsageSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_SORT, HttpStatus.BAD_REQUEST);
        }
    }

    // Descendente; los empates y las fechas vacias (nadie entro todavia) van al final, por identificador
    private static Comparator<TenantUsageDto> comparator(UsageSort sort) {
        Comparator<TenantUsageDto> primary = switch (sort) {
            case USERS -> Comparator.comparingLong(TenantUsageDto::users).reversed();
            case ACTIVEUSERS -> Comparator.comparingLong(TenantUsageDto::activeUsers).reversed();
            case LASTACTIVITY -> Comparator.comparing(TenantUsageDto::lastActivity,
                    Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder()));
            case CREATEDAT -> Comparator.comparing(TenantUsageDto::createdAt,
                    Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder()));
        };
        return primary.thenComparing(TenantUsageDto::identifier);
    }
}
