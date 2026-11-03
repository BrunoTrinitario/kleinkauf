package com.kleinkauf.product;

import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.dto.catalog.CategoryDto;
import com.kleinkauf.product.services.CatalogCacheService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogCacheServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryDto.class);
	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, Object> values = mock(ValueOperations.class);
	private final CatalogCacheService cache = new CatalogCacheService();
	private final AtomicInteger dbReads = new AtomicInteger();

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(values);
		ReflectionTestUtils.setField(cache, "redisTemplate", redis);
		ReflectionTestUtils.setField(cache, "objectMapper", objectMapper);
		TenantContext.setCurrentTenant("tienda_a");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void hit_noVaALaBase() throws Exception {
		when(values.get("catalog:tienda_a:version")).thenReturn(3);
		when(values.get("catalog:tienda_a:v3:categories"))
				.thenReturn(objectMapper.writeValueAsString(List.of(new CategoryDto("comedor", 2))));

		List<CategoryDto> result = cache.get("categories", type, this::fromDb);

		assertEquals(List.of(new CategoryDto("comedor", 2)), result);
		assertEquals(0, dbReads.get());
	}

	@Test
	void miss_leeLaBaseYGuardaConLaVersionActualYTtl() throws Exception {
		when(values.get("catalog:tienda_a:version")).thenReturn(null);

		List<CategoryDto> result = cache.get("categories", type, this::fromDb);

		assertEquals(1, dbReads.get());
		assertEquals(fromDbValue(), result);
		verify(values).set("catalog:tienda_a:v0:categories", objectMapper.writeValueAsString(fromDbValue()), Duration.ofHours(1));
	}

	@Test
	void redisCaido_igualRespondeDesdeLaBase() {
		when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

		assertEquals(fromDbValue(), cache.get("categories", type, this::fromDb));
		verify(values, never()).set(anyString(), any(), any(Duration.class));
	}

	@Test
	void invalidar_sinTransaccion_subeLaVersionDelTenant() {
		cache.invalidate();

		verify(values).increment("catalog:tienda_a:version");
	}

	@Test
	void invalidar_conTransaccion_esperaAlCommit() {
		TransactionSynchronizationManager.initSynchronization();

		cache.invalidate();

		verify(values, never()).increment(anyString());
		for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
			sync.afterCommit();
		}
		verify(values).increment(eq("catalog:tienda_a:version"));
	}

	private List<CategoryDto> fromDb() {
		dbReads.incrementAndGet();
		return fromDbValue();
	}

	private static List<CategoryDto> fromDbValue() {
		return List.of(new CategoryDto("living", 1));
	}
}
