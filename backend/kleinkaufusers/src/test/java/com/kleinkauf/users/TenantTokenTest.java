package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.UserRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tokens atados al tenant y roles que no se pueden asignar desde una tienda
@ExtendWith(MockitoExtension.class)
class TenantTokenTest {

	@Mock
	private JwtUtil jwtUtilMock;

	@Mock
	private UserRepository userRepository;

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
		String reset = jwtUtil.generatePasswordToken(1L, "ana@mail.com", "tienda_a", "hash");

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

	// Un token de sesion no sirve para cambiar la contrasena sin saber la actual
	@Test
	void resetDeContrasenia_conTokenDeSesion_respondeUnauthorized() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		String access = jwtUtil.generateAccessToken(1L, "ana@mail.com", "Ana", "tienda_a");

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword(access, "nueva123"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
		verify(userRepository, never()).save(any());
	}

	// El link del mail sirve una sola vez: cuando la contrasena cambia, el token deja de servir
	@Test
	void resetDeContrasenia_linkYaUsado_respondeUnauthorized() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		String reset = jwtUtil.generatePasswordToken(1L, "ana@mail.com", "tienda_a", "hash-viejo");
		User user = new User();
		user.setId(1L);
		user.setPassword("hash-nuevo");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword(reset, "nueva123"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
		verify(userRepository, never()).save(any());
	}

	@Test
	void tokenDeRecuperacion_llevaSuTipo() {
		JwtUtil jwtUtil = realJwtUtil();

		assertEquals(TokenConstants.PASSWORD_RESET, jwtUtil.getType(jwtUtil.generatePasswordToken(1L, "a@b.com", "t", "hash")));
		assertEquals(TokenConstants.ACCESS, jwtUtil.getType(jwtUtil.generateAccessToken(1L, "a@b.com", "Ana", "t")));
		assertEquals(JwtUtil.passwordFingerprint("hash"),
				jwtUtil.getPasswordFingerprint(jwtUtil.generatePasswordToken(1L, "a@b.com", "t", "hash")));
	}

	private static JwtUtil realJwtUtil() {
		JwtUtil jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secret", "MiSuperSecretoUltraSeguro123456789");
		jwtUtil.init();
		return jwtUtil;
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
