package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.dtos.platform.PeriodCountDto;
import com.kleinkauf.users.dtos.platform.PlatformDashboardDto;
import com.kleinkauf.users.dtos.platform.TenantCountsDto;
import com.kleinkauf.users.dtos.platform.TenantUsageDto;
import com.kleinkauf.users.entities.Tenant;
import com.kleinkauf.users.repositories.TenantRepository;
import com.kleinkauf.users.services.PlatformDashboardService;
import com.kleinkauf.users.util.CacheUtil;
import com.kleinkauf.users.util.RecentActivity;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kleinkauf.users.util.DashboardTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformDashboardServiceTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private CacheUtil cacheUtil;

	@Mock
	private RecentActivity recentActivity;

	// Paneles en hora argentina, servidor en UTC (como en Docker)
	@Spy
	private DashboardTime dashboardTime = new DashboardTime(ZoneId.of("America/Argentina/Buenos_Aires"), ZoneOffset.UTC);

	@InjectMocks
	private PlatformDashboardService service;

	// Creada el 2 a la 1:30 UTC: en Argentina todavia es el 1 a las 22:30
	@Test
	void altas_seCuentanEnElDiaDeArgentina() throws ApiException {
		when(tenantRepository.findAll()).thenReturn(List.of(tenant(1L, "a", true, "2026-10-02T01:30", null)));

		PlatformDashboardDto dashboard = service.dashboard("2026-10-01", "2026-10-02", "DAY");

		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 10, 1), 1),
				new PeriodCountDto(LocalDate.of(2026, 10, 2), 0)), dashboard.registrations().periods());
	}

	@Test
	void resumen_cuentaEstadosYAltasIncluidosLosBorrados() throws ApiException {
		when(tenantRepository.findAll()).thenReturn(List.of(
				tenant(1L, "a", true, "2026-10-01T10:00", null),
				tenant(2L, "b", false, "2026-10-01T18:00", null),
				tenant(3L, "c", true, "2026-10-03T09:00", "2026-10-20T09:00"),
				tenant(4L, "d", true, "2026-09-01T09:00", null)));

		PlatformDashboardDto dashboard = service.dashboard("2026-10-01", "2026-10-03", "DAY");

		assertEquals(new TenantCountsDto(3, 2, 1, 1), dashboard.tenants());
		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 10, 1), 2),
				new PeriodCountDto(LocalDate.of(2026, 10, 2), 0),
				new PeriodCountDto(LocalDate.of(2026, 10, 3), 1)), dashboard.registrations().periods());
	}

	@Test
	void uso_desdeLaCache_ordenaPorElCriterioPedido() throws ApiException {
		when(cacheUtil.getPlatformUsage()).thenReturn(List.of(
				usage("a", 5, 1, null),
				usage("b", 9, 0, OffsetDateTime.parse("2026-10-01T10:00Z")),
				usage("c", 2, 2, OffsetDateTime.parse("2026-10-30T10:00Z"))));

		assertEquals(List.of("b", "a", "c"), identifiers(service.usage(null)));
		assertEquals(List.of("c", "a", "b"), identifiers(service.usage("activeUsers")));
		// Los que nunca tuvieron actividad van al final
		assertEquals(List.of("c", "b", "a"), identifiers(service.usage("LASTACTIVITY")));
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void uso_sinCache_cuentaCadaSchemaYLoGuarda() throws ApiException {
		// Sin stub Mockito devolveria una lista vacia, que es una cache valida
		when(cacheUtil.getPlatformUsage()).thenReturn(null);
		when(tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(
				tenant(1L, "mi_tienda", true, "2026-10-01T10:00", null),
				tenant(2L, "sin_tabla", false, "2026-10-02T10:00", null)));
		when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("public", "mi_tienda"));
		// Solo mi_tienda tiene la tabla: sin_tabla cuenta 0 sin consultar su schema
		when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any()))
				.thenAnswer(inv -> {
					String sql = inv.getArgument(0);
					assertEquals(true, sql.contains("FROM mi_tienda.usuarios"));
					return null;
				});

		List<TenantUsageDto> usage = service.usage(null);

		assertEquals(2, usage.size());
		TenantUsageDto sinTabla = usage.stream().filter(u -> u.schema().equals("sin_tabla")).findFirst().orElseThrow();
		assertEquals("sin-tabla", sinTabla.identifier());
		assertEquals(0, sinTabla.users());
		verify(cacheUtil).savePlatformUsage(any());
	}

	@Test
	void uso_laUltimaActividadEsLoMasRecienteEntreElLoginYLasRequests() throws ApiException {
		when(cacheUtil.getPlatformUsage()).thenReturn(null);
		when(tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(
				tenant(1L, "con_sesion", true, "2026-10-01T10:00", null),
				tenant(2L, "sin_requests", true, "2026-10-02T10:00", null),
				tenant(3L, "sin_login", true, "2026-10-03T10:00", null)));
		when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
				.thenReturn(List.of("con_sesion", "sin_requests", "sin_login"));
		LocalDateTime login = LocalDateTime.parse("2026-10-20T10:00");
		LocalDateTime request = LocalDateTime.parse("2026-11-03T09:30");
		when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any()))
				.thenAnswer(inv -> {
					String sql = inv.getArgument(0);
					RowMapper<?> mapper = inv.getArgument(1);
					ResultSet rs = mock(ResultSet.class);
					when(rs.getLong("users")).thenReturn(1L);
					when(rs.getTimestamp("last")).thenReturn(sql.contains("sin_login.") ? null : Timestamp.valueOf(login));
					return mapper.mapRow(rs, 0);
				});
		when(recentActivity.byTenant()).thenReturn(Map.of("con_sesion", request, "sin_login", request));

		Map<String, OffsetDateTime> last = new HashMap<>();
		service.usage(null).forEach(u -> last.put(u.schema(), u.lastActivity()));

		assertEquals(request, last.get("con_sesion").toLocalDateTime());
		assertEquals(login, last.get("sin_requests").toLocalDateTime());
		assertEquals(request, last.get("sin_login").toLocalDateTime());
	}

	@Test
	void uso_ordenInvalido_respondeBadRequest() {
		ApiException ex = assertThrows(ApiException.class, () -> service.usage("products"));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verify(cacheUtil, never()).getPlatformUsage();
	}

	private static List<String> identifiers(List<TenantUsageDto> usage) {
		return usage.stream().map(TenantUsageDto::identifier).toList();
	}

	private static TenantUsageDto usage(String identifier, long users, long active, OffsetDateTime lastActivity) {
		return new TenantUsageDto(1L, identifier, identifier, true, OffsetDateTime.parse("2026-10-01T10:00Z"),
				users, active, lastActivity);
	}

	private static Tenant tenant(Long id, String schema, boolean enabled, String createdAt, String deletedAt) {
		Tenant tenant = new Tenant();
		tenant.setId(id);
		tenant.setSchema(schema);
		tenant.setEnable(enabled);
		tenant.setCreatedAt(LocalDateTime.parse(createdAt));
		tenant.setDeletedAt(deletedAt == null ? null : LocalDateTime.parse(deletedAt));
		return tenant;
	}
}
