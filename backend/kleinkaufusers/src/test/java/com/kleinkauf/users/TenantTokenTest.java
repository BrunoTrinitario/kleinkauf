package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.util.CacheUtil;
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

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tokens bound to the tenant and roles that cannot be assigned from a store
@ExtendWith(MockitoExtension.class)
class TenantTokenTest {

	@Mock
	private JwtUtil jwtUtilMock;

	@Mock
	private UserRepository userRepository;

	// To build the refresh session (roles and their cache)
	@Mock
	private RoleRepository roleRepository;

	@Mock
	private CacheUtil cacheUtil;

	@InjectMocks
	private UserService userService;

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void tokensCarryTheTenant() {
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
	void passwordReset_withTokenFromAnotherTenant_respondsUnauthorized() {
		TenantContext.setCurrentTenant("tienda_b");
		when(jwtUtilMock.getUserId("token")).thenReturn(1L);
		when(jwtUtilMock.getTenant("token")).thenReturn("tienda_a");

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword("token", "nueva"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
	}

	// A session token does not work to change the password without knowing the current one
	@Test
	void passwordReset_withSessionToken_respondsUnauthorized() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		String access = jwtUtil.generateAccessToken(1L, "ana@mail.com", "Ana", "tienda_a");

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword(access, "nueva123"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
		verify(userRepository, never()).save(any());
	}

	// The email link works only once: when the password changes, the token stops working
	@Test
	void passwordReset_linkAlreadyUsed_respondsUnauthorized() {
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
	void recoveryToken_carriesItsType() {
		JwtUtil jwtUtil = realJwtUtil();

		assertEquals(TokenConstants.PASSWORD_RESET, jwtUtil.getType(jwtUtil.generatePasswordToken(1L, "a@b.com", "t", "hash")));
		assertEquals(TokenConstants.ACCESS, jwtUtil.getType(jwtUtil.generateAccessToken(1L, "a@b.com", "Ana", "t")));
		assertEquals(JwtUtil.passwordFingerprint("hash"),
				jwtUtil.getPasswordFingerprint(jwtUtil.generatePasswordToken(1L, "a@b.com", "t", "hash")));
	}

	// The refresh token renews the session with new tokens
	@Test
	void refresh_valid_returnsNewTokens() throws Exception {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		User user = new User();
		user.setId(1L);
		user.setName("Ana");
		user.setEmail("ana@mail.com");
		user.setPassword("hash");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		String refresh = jwtUtil.generateRefreshToken(1L, "tienda_a", "hash");
		String tokenId = jwtUtil.getTokenId(refresh);
		when(cacheUtil.isRefreshTokenActive("tienda_a", 1L, tokenId)).thenReturn(true);

		var session = userService.refresh(refresh);

		assertEquals(TokenConstants.ACCESS, jwtUtil.getType(session.token()));
		assertEquals(TokenConstants.REFRESH, jwtUtil.getType(session.refreshToken()));
		assertEquals("tienda_a", jwtUtil.getTenant(session.token()));
		// Rotation: the old one stays a few seconds and the new one is stored for 7 days
		verify(cacheUtil).retireRefreshToken(eq("tienda_a"), eq(1L), eq(tokenId), any());
		verify(cacheUtil).saveRefreshToken("tienda_a", 1L, jwtUtil.getTokenId(session.refreshToken()),
				Duration.ofMillis(JwtUtil.REFRESH_TOKEN_MILLIS));
		assertNotEquals(tokenId, jwtUtil.getTokenId(session.refreshToken()));
	}

	// After logout (or after rotating it and the grace period passing) the refresh token is no longer in Redis
	@Test
	void refresh_revoked_respondsUnauthorized() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");

		ApiException ex = assertThrows(ApiException.class,
				() -> userService.refresh(jwtUtil.generateRefreshToken(1L, "tienda_a", "hash")));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
		verify(userRepository, never()).findById(any());
	}

	@Test
	void logout_deletesThatSessionRefresh() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		String refresh = jwtUtil.generateRefreshToken(1L, "tienda_a", "hash");

		userService.logout(refresh);
		// One from another store or a session token delete nothing
		userService.logout(jwtUtil.generateRefreshToken(1L, "tienda_b", "hash"));
		userService.logout(jwtUtil.generateAccessToken(1L, "ana@mail.com", "Ana", "tienda_a"));
		userService.logout(null);

		verify(cacheUtil).deleteRefreshToken("tienda_a", 1L, jwtUtil.getTokenId(refresh));
		verify(cacheUtil, times(1)).deleteRefreshToken(any(), any(), any());
	}

	// Neither a session token, nor one from another store, nor one from before the password change
	@Test
	void refresh_withUnusableToken_respondsUnauthorized() {
		JwtUtil jwtUtil = realJwtUtil();
		ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
		TenantContext.setCurrentTenant("tienda_a");
		User user = new User();
		user.setId(1L);
		user.setPassword("hash-nuevo");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(cacheUtil.isRefreshTokenActive(eq("tienda_a"), eq(1L), any())).thenReturn(true);

		for (String token : java.util.List.of(
				jwtUtil.generateAccessToken(1L, "ana@mail.com", "Ana", "tienda_a"),
				jwtUtil.generateRefreshToken(1L, "tienda_b", "hash-nuevo"),
				jwtUtil.generateRefreshToken(1L, "tienda_a", "hash-viejo"))) {
			ApiException ex = assertThrows(ApiException.class, () -> userService.refresh(token));
			assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
		}
	}

	private static JwtUtil realJwtUtil() {
		JwtUtil jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secret", "MiSuperSecretoUltraSeguro123456789");
		jwtUtil.init();
		return jwtUtil;
	}

	@Test
	void passwordReset_withInvalidToken_respondsUnauthorized() {
		TenantContext.setCurrentTenant("tienda_a");
		when(jwtUtilMock.getUserId("basura")).thenReturn(null);

		ApiException ex = assertThrows(ApiException.class, () -> userService.refreshPassword("basura", "nueva"));

		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
	}

	@Test
	void superadminIsNotAssignedFromAStore() {
		assertFalse(RoleUtility.existsRole("SUPERADMIN"));
		assertTrue(RoleUtility.existsRole("ADMINISTRADOR"));
		assertTrue(RoleUtility.existsRole("EMPLEADO"));
		assertTrue(RoleUtility.existsRole("CLIENTE"));
		assertFalse(RoleUtility.existsRole("INVENTADO"));
		assertFalse(RoleUtility.existsRole(null));
	}
}
