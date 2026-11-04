package com.kleinkauf.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.users.dtos.CachedUser;
import com.kleinkauf.users.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los roles que users deja en Redis: los leen products, sales y el gateway en vez de preguntarle a users
class CachedUserTest {

	@Test
	@SuppressWarnings("unchecked")
	void guardaLosRolesEnRedis() {
		RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
		HashOperations<String, Object, Object> hash = mock(HashOperations.class);
		when(redisTemplate.opsForHash()).thenReturn(hash);
		CacheUtil cacheUtil = new CacheUtil();
		ReflectionTestUtils.setField(cacheUtil, "redisTemplate", redisTemplate);
		ReflectionTestUtils.setField(cacheUtil, "objectMapper", new ObjectMapper());

		CachedUser cached = new CachedUser(7L, "mi_tienda", List.of(new RoleDto("ADMINISTRADOR", 31)));
		cacheUtil.saveOrUpdateUserCache(cached);

		assertEquals(1, cached.getRoles().size());
		// Antes quedaba "null": el constructor no guardaba los roles
		verify(hash).put("user:7:mi_tienda", "roles", "[{\"type\":\"ADMINISTRADOR\",\"permissions\":31}]");
		verify(redisTemplate).expire("user:7:mi_tienda", Duration.ofHours(1));
	}
}
