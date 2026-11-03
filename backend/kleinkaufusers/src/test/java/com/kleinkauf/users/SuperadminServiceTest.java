package com.kleinkauf.users;

import java.util.Map;
import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.dtos.platform.NewSuperadminDto;
import com.kleinkauf.users.dtos.platform.SuperadminDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.services.SuperadminService;
import com.kleinkauf.users.util.CacheUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperadminServiceTest {

	private static final long ME = 1L;
	private static final long OTHER = 2L;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private CacheUtil cacheUtil;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private SuperadminService service;

	@Test
	void alta_creaUsuarioYRolSuperadmin() throws ApiException {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("nuevo@kleinkauf.com"))).thenReturn(0L);
		when(passwordEncoder.encode("clave123")).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User user = inv.getArgument(0);
			user.setId(5L);
			return user;
		});

		SuperadminDto created = service.create(new NewSuperadminDto(" Nuevo ", " nuevo@kleinkauf.com ", "clave123"));

		assertEquals(5L, created.id());
		assertEquals("Nuevo", created.name());
		assertEquals("nuevo@kleinkauf.com", created.email());
		assertTrue(created.active());
		ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);
		verify(roleRepository).save(role.capture());
		assertEquals("SUPERADMIN", role.getValue().getType());
		assertEquals(5L, role.getValue().getUser().getId());
	}

	@Test
	void alta_datosInvalidos_respondeBadRequest() {
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
				() -> service.create(new NewSuperadminDto("Nuevo", "nuevo@kleinkauf.com", "corta"))).getStatus());
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
				() -> service.create(new NewSuperadminDto("", "nuevo@kleinkauf.com", "clave123"))).getStatus());
		verify(userRepository, never()).save(any());
	}

	@Test
	void alta_emailUsadoTambienPorUnBorrado_respondeConflict() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("viejo@kleinkauf.com"))).thenReturn(1L);

		ApiException ex = assertThrows(ApiException.class,
				() -> service.create(new NewSuperadminDto("Viejo", "viejo@kleinkauf.com", "clave123")));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
		verify(userRepository, never()).save(any());
	}

	@Test
	void desactivarseASiMismo_respondeBadRequest() {
		superadmins(superadmin(ME, true), superadmin(OTHER, true));

		ApiException ex = assertThrows(ApiException.class, () -> service.setActive(ME, false, ME));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void desactivarAlUltimoActivo_respondeConflict() {
		// Otro superadmin de un token viejo: el unico activo es OTHER
		superadmins(superadmin(ME, false), superadmin(OTHER, true));
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("SUPERADMIN"))).thenReturn(1L);

		ApiException ex = assertThrows(ApiException.class, () -> service.setActive(OTHER, false, ME));

		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
	}

	@Test
	void desactivar_borraUsuarioYRolYSusRolesCacheados() throws ApiException {
		superadmins(superadmin(ME, true), superadmin(OTHER, true));
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("SUPERADMIN"))).thenReturn(2L);

		service.setActive(OTHER, false, ME);

		verify(jdbcTemplate).update(eq("UPDATE public.usuarios SET deleted_at = ? WHERE id = ?"), any(), eq(OTHER));
		verify(jdbcTemplate).update(eq("UPDATE public.roles SET deleted_at = ? WHERE userid = ? AND type = ?"),
				any(), eq(OTHER), eq("SUPERADMIN"));
		verify(cacheUtil).deleteUserRoles(OTHER, null);
		// Va con SQL directo: se registra a mano
		verify(auditLog).record(AuditEvent.DEACTIVATE, "Superadmin", OTHER,
				Map.of("active", new AuditEvent.Change("true", "false")));
	}

	@Test
	void reactivar_limpiaLosDeletedAt() throws ApiException {
		superadmins(superadmin(ME, true), superadmin(OTHER, false));

		service.setActive(OTHER, true, ME);

		verify(jdbcTemplate).update(eq("UPDATE public.usuarios SET deleted_at = ? WHERE id = ?"), isNull(), eq(OTHER));
		verify(jdbcTemplate).update(eq("UPDATE public.roles SET deleted_at = ? WHERE userid = ? AND type = ?"),
				isNull(), eq(OTHER), eq("SUPERADMIN"));
		verify(cacheUtil, never()).deleteUserRoles(any(), any());
	}

	@Test
	void sinCambio_noToca() throws ApiException {
		superadmins(superadmin(ME, true), superadmin(OTHER, true));

		assertTrue(service.setActive(OTHER, true, ME).active());
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void idQueNoEsSuperadmin_respondeNotFound() {
		superadmins(superadmin(ME, true));

		ApiException ex = assertThrows(ApiException.class, () -> service.setActive(99L, false, ME));

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
		assertFalse(ex.getMessage() == null);
	}

	// La lista sale de una consulta nativa: el mock devuelve las filas ya mapeadas
	@SuppressWarnings("unchecked")
	private void superadmins(SuperadminDto... rows) {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("SUPERADMIN"))).thenReturn(new ArrayList<>(List.of(rows)));
	}

	private static SuperadminDto superadmin(long id, boolean active) {
		return new SuperadminDto(id, "Root " + id, "root" + id + "@kleinkauf.com", active, OffsetDateTime.parse("2026-10-01T10:00Z"), null);
	}
}
