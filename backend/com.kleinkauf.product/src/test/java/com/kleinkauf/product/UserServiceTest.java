package com.kleinkauf.product;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.services.LocalCacheService;
import com.kleinkauf.product.services.UserService;
import com.kleinkauf.product.services.UsersClient;
import com.kleinkauf.product.util.CacheUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final String TENANT = "tienda1";

	@Mock
	private CacheUtil cache;

	@Mock
	private UsersClient usersClient;

	@Mock
	private LocalCacheService localCacheService;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setTenant() {
		TenantContext.setCurrentTenant(TENANT);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	// Los caches devuelven null cuando no tienen el dato (Mockito por defecto devuelve lista vacia)
	private void sinRolesEnCache() {
		when(localCacheService.getUserRoles(1L, TENANT)).thenReturn(null);
		when(cache.getUserRoles(1L, TENANT)).thenReturn(null);
	}

	@Test
	void getUserRolesById_usersCaido_devuelveListaVaciaYNoCachea() {
		sinRolesEnCache();
		when(usersClient.getUserRoles(1L, TENANT)).thenReturn(null);

		List<RoleDto> roles = userService.getUserRolesById(1L);

		assertTrue(roles.isEmpty());
		verify(localCacheService, never()).saveUserRoles(anyLong(), anyString(), any());
	}

	@Test
	void getUserRolesById_rolesDeUsers_seCacheanLocal() {
		sinRolesEnCache();
		List<RoleDto> remotos = List.of(new RoleDto("ADMINISTRADOR", 1));
		when(usersClient.getUserRoles(1L, TENANT)).thenReturn(remotos);

		List<RoleDto> roles = userService.getUserRolesById(1L);

		assertSame(remotos, roles);
		verify(localCacheService).saveUserRoles(1L, TENANT, remotos);
	}

	@Test
	void getUserRolesById_enCacheLocal_noLlamaAUsers() {
		List<RoleDto> cacheados = List.of(new RoleDto("CLIENTE", 0));
		when(localCacheService.getUserRoles(1L, TENANT)).thenReturn(cacheados);

		assertSame(cacheados, userService.getUserRolesById(1L));
		verifyNoInteractions(usersClient);
	}

	@Test
	void getTenant_inexistente_devuelveNullYNoCachea() {
		when(usersClient.getTenant("nada")).thenReturn(null);

		assertNull(userService.getTenant("nada"));
		verify(localCacheService, never()).saveTenant(any());
	}

	@Test
	void getTenant_deUsers_seCacheaLocal() {
		TenantDto tenant = new TenantDto(1L, "cfg", TENANT, true, null, null);
		when(usersClient.getTenant(TENANT)).thenReturn(tenant);

		assertEquals(tenant, userService.getTenant(TENANT));
		verify(localCacheService).saveTenant(tenant);
	}

	@Test
	void getTenants_redisVacio_consultaAUsers() {
		List<TenantDto> remotos = List.of(new TenantDto(1L, "cfg", TENANT, true, null, null));
		when(cache.getAllTenants()).thenReturn(List.of());
		when(usersClient.getTenants()).thenReturn(remotos);

		assertSame(remotos, userService.getTenants());
	}
}
