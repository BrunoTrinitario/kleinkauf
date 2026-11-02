package com.decor.product;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.dto.platform.ProductUsageDto;
import com.decor.product.services.PlatformUsageService;
import com.decor.product.util.CacheUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformUsageServiceTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private CacheUtil cacheUtil;

	@InjectMocks
	private PlatformUsageService service;

	@Test
	void cuentaCadaSchemaMenosPublic_yLoGuarda() throws ApiException {
		when(cacheUtil.getPlatformUsage()).thenReturn(null);
		when(jdbcTemplate.queryForList(contains("'product'"), eq(String.class)))
				.thenReturn(List.of("demo", "public", "sin_combos", "Mal-Schema"));
		when(jdbcTemplate.queryForList(contains("'bundles'"), eq(String.class))).thenReturn(List.of("demo", "public"));
		when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demo.product WHERE deleted_at IS NULL", Long.class)).thenReturn(12L);
		when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demo.bundles", Long.class)).thenReturn(3L);
		when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sin_combos.product WHERE deleted_at IS NULL", Long.class)).thenReturn(4L);

		List<ProductUsageDto> usage = service.usage(null);

		assertEquals(List.of(new ProductUsageDto("demo", 12, 3), new ProductUsageDto("sin_combos", 4, 0)), usage);
		verify(jdbcTemplate, never()).queryForObject(contains("public."), eq(Long.class));
		verify(cacheUtil).savePlatformUsage(usage);
	}

	@Test
	void desdeLaCache_ordenaPorCombos() throws ApiException {
		when(cacheUtil.getPlatformUsage()).thenReturn(List.of(
				new ProductUsageDto("a", 10, 1), new ProductUsageDto("b", 2, 5)));

		assertEquals("b", service.usage("bundles").get(0).schema());
		assertEquals("a", service.usage("PRODUCTS").get(0).schema());
		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void ordenInvalido_respondeBadRequest() {
		ApiException ex = assertThrows(ApiException.class, () -> service.usage("users"));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class));
	}
}
