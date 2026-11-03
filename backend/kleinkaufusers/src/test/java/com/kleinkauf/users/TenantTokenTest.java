package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.JwtUtil;
import com.kleinkauf.users.util.RoleUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// Tokens atados al tenant y roles que no se pueden asignar desde una tienda
@ExtendWith(MockitoExtension.class)
class TenantTokenTest {

	@Mock
	private JwtUtil jwtUtilMock;

	@InjectMocks
	private UserService userService;

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void losTokensLlevanElTenant() {
		JwtUtil jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secret", "MiSuperSecretoUltraSeguro123456789");
		jwtUtil.init();

		String access = jwtUtil.generateAccessToken(1L, "ana@mail.com", "Ana", "tienda_a");
		String reset = jwtUtil.generatePasswordToken(1L, "ana@mail.com", "tienda_a");

		assertEquals("tienda_a", jwtUtil.getTenant(access));
		assertEquals("tienda_a", jwtUtil.getTenant(reset));
		assertEquals(1L, jwtUtil.getUserId(access));
	}

	@Test
	void resetDeContrasenia_conTokenDeOtroTenant_respondeUnauthorized() {
		TenantContext.setCurrentTenant("tienda_b");
		when(jwtUtilMock.getUserId("token")).thenReturn(1L);
		when(jwtUtilMock.getTenant("token")).thenReturn("tienda_a");

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword("token", "nueva"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
	}

	@Test
	void resetDeContrasenia_conTokenInvalido_respondeUnauthorized() {
		TenantContext.setCurrentTenant("tienda_a");
		when(jwtUtilMock.getUserId("basura")).thenReturn(null);

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword("basura", "nueva"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
	}

	@Test
	void superadminNoSeAsignaDesdeUnaTienda() {
		assertFalse(RoleUtility.existsRole("SUPERADMIN"));
		assertTrue(RoleUtility.existsRole("ADMINISTRADOR"));
		assertTrue(RoleUtility.existsRole("EMPLEADO"));
		assertTrue(RoleUtility.existsRole("CLIENTE"));
		assertFalse(RoleUtility.existsRole("INVENTADO"));
		assertFalse(RoleUtility.existsRole(null));
	}
}
