package com.decor.users;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.dtos.platform.PeriodCountDto;
import com.decor.users.dtos.platform.PlatformDashboardDto;
import com.decor.users.dtos.platform.TenantCountsDto;
import com.decor.users.dtos.platform.TenantUsageDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.services.PlatformDashboardService;
import com.decor.users.util.CacheUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

	@InjectMocks
	private PlatformDashboardService service;

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
