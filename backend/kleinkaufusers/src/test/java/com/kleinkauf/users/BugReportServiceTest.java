package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.dtos.NewBugReportDto;
import com.kleinkauf.users.dtos.platform.BugReportDto;
import com.kleinkauf.users.dtos.platform.BugReportPageDto;
import com.kleinkauf.users.entities.BugReport;
import com.kleinkauf.users.entities.Tenant;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.BugReportRepository;
import com.kleinkauf.users.repositories.TenantRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.services.BugReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Reporting a bug from a store and the platform dashboard listing
@ExtendWith(MockitoExtension.class)
class BugReportServiceTest {

	@Mock
	private BugReportRepository bugReportRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private TenantRepository tenantRepository;

	@InjectMocks
	private BugReportService bugReportService;

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void guest_storesTheStoreAndTheEmailTheyWrote() throws Exception {
		TenantContext.setCurrentTenant("mi_tienda");

		bugReportService.create(new NewBugReportDto("  El carrito no suma bien  ", " Ana@Mail.com ", "https://mi-tienda.x/carrito"),
				null, "Mozilla/5.0");

		BugReport saved = saved();
		assertEquals("mi_tienda", saved.getTenantSchema());
		assertEquals("El carrito no suma bien", saved.getDescription());
		assertEquals("ana@mail.com", saved.getEmail());
		assertEquals("https://mi-tienda.x/carrito", saved.getPageUrl());
		assertEquals("Mozilla/5.0", saved.getUserAgent());
		assertNull(saved.getUserId());
	}

	@Test
	void withSession_storesWhoItIsAndUsesTheAccountEmail() throws Exception {
		TenantContext.setCurrentTenant("mi_tienda");
		User user = new User();
		user.setId(7L);
		user.setName("Ana");
		user.setEmail("ana@mail.com");
		when(userRepository.findById(7L)).thenReturn(Optional.of(user));

		bugReportService.create(new NewBugReportDto("No carga la foto del producto", null, null), 7L, null);

		BugReport saved = saved();
		assertEquals(7L, saved.getUserId());
		assertEquals("Ana", saved.getReporterName());
		assertEquals("ana@mail.com", saved.getEmail());
	}

	@Test
	void shortOrLongDescription_orInvalidEmail_respondsBadRequest() {
		for (NewBugReportDto dto : List.of(
				new NewBugReportDto("corto", null, null),
				new NewBugReportDto("x".repeat(2001), null, null),
				new NewBugReportDto("El carrito no suma bien", "no-es-un-email", null))) {
			ApiException ex = assertThrows(ApiException.class, () -> bugReportService.create(dto, null, null));
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		}
		verify(bugReportRepository, never()).save(any());
	}

	@Test
	void longPageUrl_isTruncated() throws Exception {
		bugReportService.create(new NewBugReportDto("El carrito no suma bien", null, "https://x/" + "a".repeat(600)), null, null);

		assertEquals(BugReport.PAGE_URL_MAX_LENGTH, saved().getPageUrl().length());
	}

	@Test
	void listing_pendingByDefault_withEachReportStore() throws Exception {
		BugReport report = report(4L, "mi_tienda");
		Tenant tenant = new Tenant();
		tenant.setSchema("mi_tienda");
		tenant.setUrl("https://mi-tienda.kleinkauf.site");
		when(bugReportRepository.findByResolvedAtIsNullOrderByCreatedAtDescIdDesc(PageRequest.of(0, 20)))
				.thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));
		when(bugReportRepository.countByResolvedAtIsNull()).thenReturn(1L);
		when(tenantRepository.findAll()).thenReturn(List.of(tenant));

		BugReportPageDto page = bugReportService.list(null, null, null);

		assertEquals(1, page.totalItems());
		assertEquals(1, page.pending());
		BugReportDto dto = page.items().get(0);
		assertEquals("mi-tienda", dto.store());
		assertEquals("https://mi-tienda.kleinkauf.site", dto.storeUrl());
	}

	@Test
	void listing_invalidStatusOrPage_respondsBadRequest() {
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ApiException.class, () -> bugReportService.list("otro", null, null)).getStatus());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ApiException.class, () -> bugReportService.list("all", -1, null)).getStatus());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ApiException.class, () -> bugReportService.list("all", 0, 101)).getStatus());
	}

	@Test
	void markResolved_doesNotChangeTheDateIfAlreadyResolved_andPendingClearsIt() throws Exception {
		LocalDateTime resolvedAt = LocalDateTime.of(2026, 11, 1, 10, 0);
		BugReport report = report(4L, null);
		report.setResolvedAt(resolvedAt);
		when(bugReportRepository.findById(4L)).thenReturn(Optional.of(report));
		when(bugReportRepository.save(report)).thenReturn(report);

		bugReportService.setResolved(4L, true);
		assertEquals(resolvedAt, report.getResolvedAt());

		bugReportService.setResolved(4L, false);
		assertNull(report.getResolvedAt());
	}

	@Test
	void markResolved_unknownReport_respondsNotFound() {
		when(bugReportRepository.findById(9L)).thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class, () -> bugReportService.setResolved(9L, true));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
	}

	private BugReport saved() {
		ArgumentCaptor<BugReport> captor = ArgumentCaptor.forClass(BugReport.class);
		verify(bugReportRepository).save(captor.capture());
		return captor.getValue();
	}

	private static BugReport report(Long id, String schema) {
		BugReport report = new BugReport();
		report.setId(id);
		report.setTenantSchema(schema);
		report.setDescription("El carrito no suma bien");
		report.setCreatedAt(LocalDateTime.of(2026, 11, 4, 9, 30));
		return report;
	}
}
